package com.taobao.f2e;

import org.apache.commons.cli.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * invoke module compiler for kissy
 *
 * @author yiminghe@gmail.com
 */
public class Main {
    static String DEP_PREFIX = "{\n    ";

    static String COMPACT_DEP_PREFIX = "{\n    ";

    static String DEP_SUFFIX = "\n}";

    private boolean compact = false;

    private HashMap<String, String> warned = new HashMap<String, String>();

    /**
     * packages.
     */
    private Packages packages = new Packages();

    /**
     * exclude pattern for processedModules.
     */
    private Pattern excludePattern;

    /**
     * stack of visited processedModules to detect circular dependency
     */
    private ArrayList<String> modulesVisited = new ArrayList<String>();

    /**
     * requires mods name for current application.
     */
    private String require = null;

    /**
     * combined mods 's code 's output file path.
     */
    private String output = "";//"d:/code/kissy_git/kissy/tools/module-compiler/tests/kissy/combine.js";

    /**
     * all processed processedModules.
     */
    private ArrayList<Module> processedModules = new ArrayList<Module>();

    /**
     * whether output dependency file path.
     */
    private String outputDependency = null;

    /**
     * dependencies
     */
    private ArrayList<String> dependencies = new ArrayList<String>();

    public void setExcludePattern(Pattern excludePattern) {
        this.excludePattern = excludePattern;
    }

    public void setOutputDependency(String outputDependency) {
        this.outputDependency = outputDependency;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public void setCompact(boolean compact) {
        this.compact = compact;
    }

    public Packages getPackages() {
        return packages;
    }

    public void setRequire(String require) {
        this.require = require;
    }

    public void run() {
        long start = System.currentTimeMillis();

        combineRequire(require);

        ArrayList<String> combinedFiles = new ArrayList<String>();
        StringBuilder finalCodes = new StringBuilder();

        for (Module m : processedModules) {
            combinedFiles.add(m.getName());
            finalCodes.append(m.getCode());
        }

        String re = "/*\n Combined modules by KISSY Module Compiler: \n\n " +
                ArrayUtils.join(combinedFiles.toArray(new String[combinedFiles.size()]), "\n ")
                + "\n*/\n\n" + finalCodes.toString();

        /*
      combined mods 's code 's output file encoding.
     */
        String outputEncoding = "utf-8";
        if (output != null) {
            FileUtils.outputContent(re, output, outputEncoding);
            System.out.println("success generated: " + output);
        } else {
            System.out.println(re);
        }

        if (outputDependency != null && dependencies.size() != 0) {
            String allRs = "";
            for (String r : dependencies) {
                allRs += ", \"" + r + "\"";
            }
            require = "\"" + require + "\"";
            re = (compact ? COMPACT_DEP_PREFIX : DEP_PREFIX) + require + ": [" +
                    allRs.substring(2) + "]" + DEP_SUFFIX;
            FileUtils.outputContent(re, outputDependency, outputEncoding);
            System.out.println("success generated: " + outputDependency);
        }

        System.out.print("duration: " + (System.currentTimeMillis() - start));
    }

    private void addDependency(String requiredModuleName) {
        if (!dependencies.contains(requiredModuleName)) {
            dependencies.add(requiredModuleName);
        }
    }

    /**
     * x -> a,b,c : x depends on a,b,c
     * add a,b,c then add x to final code buffer
     *
     * @param requiredModuleName module name required
     */
    private void combineRequire(String requiredModuleName) {
        // if css file, do not combine with js files
        if (requiredModuleName.endsWith(".css")) {
            this.addDependency(requiredModuleName);
            return;
        }

        // if specify exclude this module, just return
        if (excludePattern != null &&
                excludePattern.matcher(requiredModuleName).matches()) {
            this.addDependency(requiredModuleName);
            return;
        }

        Module requiredModule = packages.getModuleFromName(requiredModuleName);

        if (requiredModule == null || !requiredModule.exists()) {
            if (!warned.containsKey(requiredModuleName)) {
                System.out.println("Warning: module not found: " + requiredModuleName);
                this.addDependency(requiredModuleName);
                warned.put(requiredModuleName, "");
            }
            return;
        }

        if (!requiredModule.isValidFormat()) {
            System.err.println("Error: invalid module: " + requiredModuleName);
            System.exit(1);
        }

        //x -> a,b,c
        //a -> b
        //reduce redundant parse and recursive
        if (processedModules.contains(requiredModule)) {
            return;
        }

        if (modulesVisited.contains(requiredModuleName)) {
            String error = "cyclic dependence: " +
                    ArrayUtils.join(modulesVisited.toArray(new String[modulesVisited.size()]),
                            ",") + "," + requiredModuleName;
            //if silence ,just return
            System.err.println(error);
            System.exit(1);
            return;
        }

        //mark as start for cyclic detection
        modulesVisited.add(requiredModuleName);

        // normalize structure
        String[] requires = requiredModule.getRequires();
        requiredModule.completeModuleName();

        for (String require : requires) {
            combineRequire(require);
        }

        //remove mark for cyclic detection
        modulesVisited.remove(modulesVisited.size() - 1);

        processedModules.add(requiredModule);
    }


    public static void commandRunnerCLI(String[] args) throws Exception {

        Options options = new Options();
        options.addOption("baseUrls", true, "baseUrls");
        options.addOption("packageUrls", true, "packageUrls");
        options.addOption("require", true, "require");
        options.addOption("excludeReg", true, "excludeReg");
        options.addOption("output", true, "output");
        options.addOption("v", "version", false, "version");
        options.addOption("outputDependency", true, "outputDependency");
        options.addOption("compact", true, "compact mode");

        // create the command line parser
        CommandLineParser parser = new GnuParser();
        CommandLine line;

        try {
            // parse the command line arguments
            line = parser.parse(options, args);
        } catch (ParseException exp) {
            System.out.println("Unexpected exception:" + exp.getMessage());
            return;
        }

        if (line.hasOption("v")) {
            System.out.println("KISSY Module Compiler 1.4");
            return;
        }


        Main builder = new Main();

        String compact = line.getOptionValue("compact");
        if (compact != null) {
            builder.compact = true;
        }

        Packages packages = builder.getPackages();

        String baseUrlStr = line.getOptionValue("baseUrls");
        if (baseUrlStr != null) {
            packages.initByBaseUrls(baseUrlStr);
        }

        String packageUrlStr = line.getOptionValue("packageUrls");
        if (packageUrlStr != null) {
            packages.initByPackageUrls(packageUrlStr);
        }

        builder.setRequire(ModuleUtils.addIndexAndRemoveJsExt(line.getOptionValue("require")));

        String excludeReg = line.getOptionValue("excludeReg");
        if (excludeReg != null) {
            builder.setExcludePattern(Pattern.compile(excludeReg));
        }

        builder.setOutput(line.getOptionValue("output"));

        builder.setOutputDependency(line.getOptionValue("outputDependency"));

        builder.run();

    }

    public static void main(String[] args) throws Exception {
        System.out.println("current path: " + new File(".").getAbsolutePath());
        System.out.println("current args: " + Arrays.toString(args));
        commandRunnerCLI(args);
    }
}
