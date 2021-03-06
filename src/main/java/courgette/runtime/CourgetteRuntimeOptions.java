package courgette.runtime;

import courgette.api.CucumberOptions;
import courgette.integration.reportportal.ReportPortalProperties;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.options.CommandlineOptionsParser;
import io.cucumber.core.options.RuntimeOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static java.util.Arrays.copyOf;

public class CourgetteRuntimeOptions {
    private final CourgetteProperties courgetteProperties;
    private final Feature feature;
    private final CucumberOptions cucumberOptions;
    private final String reportTargetDir;

    private List<String> runtimeOptions = new ArrayList<>();
    private String rerunFile;
    private String cucumberResourcePath;

    private final int instanceId = UUID.randomUUID().hashCode();

    public CourgetteRuntimeOptions(CourgetteProperties courgetteProperties, Feature feature) {
        this.courgetteProperties = courgetteProperties;
        this.feature = feature;
        this.cucumberOptions = courgetteProperties.getCourgetteOptions().cucumberOptions();
        this.cucumberResourcePath = feature.getUri().getSchemeSpecificPart();
        this.reportTargetDir = courgetteProperties.getCourgetteOptions().reportTargetDir();

        createRuntimeOptions(cucumberOptions, cucumberResourcePath).forEach((key, value) -> runtimeOptions.addAll(value));
    }

    public CourgetteRuntimeOptions(CourgetteProperties courgetteProperties) {
        this.courgetteProperties = courgetteProperties;
        this.cucumberOptions = courgetteProperties.getCourgetteOptions().cucumberOptions();
        this.feature = null;
        this.reportTargetDir = courgetteProperties.getCourgetteOptions().reportTargetDir();

        createRuntimeOptions(cucumberOptions, null).forEach((key, value) -> runtimeOptions.addAll(value));
    }

    public RuntimeOptions getRuntimeOptions() {
        return new CommandlineOptionsParser().parse(runtimeOptions).build();
    }

    public Map<String, List<String>> mapRuntimeOptions() {
        return createRuntimeOptions(cucumberOptions, cucumberResourcePath);
    }

    public String getRerunFile() {
        return rerunFile;
    }

    public String getCucumberRerunFile() {
        final String cucumberRerunFile = cucumberRerunPlugin.apply(courgetteProperties);

        if (cucumberRerunFile == null) {
            return getRerunFile();
        }
        return cucumberRerunFile;
    }

    public List<String> getReportJsFiles() {
        final List<String> reportFiles = new ArrayList<>();

        runtimeOptions.forEach(option -> {
            if (option != null && isReportPlugin.test(option)) {
                String reportFile = option.substring(option.indexOf(":") + 1);

                if (option.startsWith("html:")) {
                    reportFile = reportFile + "/report.js";
                }
                reportFiles.add(reportFile);
            }
        });
        return reportFiles;
    }

    public String getCourgetteReportDataDirectory() {
        return reportTargetDir + "/courgette-report/data";
    }

    public String getCourgetteReportJson() {
        return String.format("%s/report.json", getCourgetteReportDataDirectory());
    }

    public String getCourgetteReportXmlForReportPortal() {
        final ReportPortalProperties reportPortalProperties = ReportPortalProperties.getInstance();
        return String.format("%s/%s.xml", getCourgetteReportDataDirectory(), reportPortalProperties.getLaunchName());
    }

    private Map<String, List<String>> createRuntimeOptions(CucumberOptions cucumberOptions, String path) {
        final Map<String, List<String>> runtimeOptions = new HashMap<>();

        runtimeOptions.put("--glue", optionParser.apply("--glue", envCucumberOptionParser.apply("glue", cucumberOptions.glue())));
        runtimeOptions.put("--extraGlue", optionParser.apply("--glue", envCucumberOptionParser.apply("extraGlue", cucumberOptions.extraGlue())));
        runtimeOptions.put("--tags", optionParser.apply("--tags", envCucumberOptionParser.apply("tags", cucumberOptions.tags())));
        runtimeOptions.put("--plugin", optionParser.apply("--plugin", parsePlugins(envCucumberOptionParser.apply("plugin", cucumberOptions.plugin()))));
        runtimeOptions.put("--name", optionParser.apply("--name", envCucumberOptionParser.apply("name", cucumberOptions.name())));
        runtimeOptions.put("--snippets", optionParser.apply("--snippets", cucumberOptions.snippets().name().toLowerCase()));
        runtimeOptions.put("--dryRun", Collections.singletonList(cucumberOptions.dryRun() ? "--dry-run" : "--no-dry-run"));
        runtimeOptions.put("--strict", Collections.singletonList(cucumberOptions.strict() ? "--strict" : "--no-strict"));
        runtimeOptions.put("--monochrome", Collections.singletonList(cucumberOptions.monochrome() ? "--monochrome" : "--no-monochrome"));
        runtimeOptions.put(null, featureParser.apply(envCucumberOptionParser.apply("features", cucumberOptions.features()), path));

        if (!cucumberOptions.objectFactory().getName().equals("courgette.runtime.CourgetteNoObjectFactory")) {
            runtimeOptions.put("--object-factory", optionParser.apply("--object-factory", cucumberOptions.objectFactory().getName()));
        }

        runtimeOptions.values().removeIf(Objects::isNull);
        return runtimeOptions;
    }

    private BiFunction<String, String[], String[]> envCucumberOptionParser = (systemPropertyName, cucumberOptions) -> {
        String cucumberOption = System.getProperty("cucumber." + systemPropertyName);

        if (cucumberOption != null && cucumberOption.trim().length() > 0) {
            final List<String> options = new ArrayList<>();
            Arrays.stream(cucumberOption.split(",")).forEach(t -> options.add(t.trim()));

            String[] cucumberOptionArray = new String[options.size()];
            return options.toArray(cucumberOptionArray);
        }
        return cucumberOptions;
    };

    private String getMultiThreadRerunFile() {
        return getTempDirectory() + courgetteProperties.getSessionId() + "_rerun_" + getFeatureId(feature) + ".txt";
    }

    private String getMultiThreadReportFile() {
        return getTempDirectory() + courgetteProperties.getSessionId() + "_thread_report_" + getFeatureId(feature);
    }

    private String getFeatureId(Feature feature) {
        return String.format("%s_%s", feature.hashCode(), instanceId);
    }

    private Function<CourgetteProperties, String> cucumberRerunPlugin = (courgetteProperties) -> {
        final String rerunPlugin = Arrays.stream(courgetteProperties.getCourgetteOptions()
                .cucumberOptions()
                .plugin()).filter(p -> p.startsWith("rerun")).findFirst().orElse(null);

        if (rerunPlugin != null) {
            return rerunPlugin.substring(rerunPlugin.indexOf(":") + 1);
        }
        return null;
    };

    private final Predicate<String> isReportPlugin = (plugin) -> plugin.startsWith("html:") || plugin.startsWith("json:") || plugin.startsWith("junit:");

    private String[] parsePlugins(String[] plugins) {
        List<String> pluginList = new ArrayList<>();

        if (plugins.length == 0) {
            plugins = new String[]{"json:" + getCourgetteReportJson()};
        }

        asList(plugins).forEach(plugin -> {
            if (isReportPlugin.test(plugin)) {
                if (feature != null) {
                    pluginList.add(plugin);

                    String extension = plugin.substring(0, plugin.indexOf(":"));

                    if (extension.equalsIgnoreCase("junit")) {
                        pluginList.remove(plugin);

                        final String reportPath = String.format("junit:%s.xml", getMultiThreadReportFile());
                        pluginList.add(reportPath);
                    } else {
                        if (!extension.equals("")) {
                            final String reportPath = String.format("%s:%s.%s", extension, getMultiThreadReportFile(), extension);
                            pluginList.add(reportPath);
                        }
                    }
                } else {
                    pluginList.add(plugin);
                }
            } else {
                pluginList.add(plugin);
            }
        });

        Predicate<List<String>> alreadyAddedRerunPlugin = (addedPlugins) -> addedPlugins.stream().anyMatch(p -> p.startsWith("rerun:"));

        if (!alreadyAddedRerunPlugin.test(pluginList)) {
            if (feature != null) {
                rerunFile = getMultiThreadRerunFile();
            } else {
                final String cucumberRerunFile = cucumberRerunPlugin.apply(courgetteProperties);
                rerunFile = cucumberRerunFile != null ? cucumberRerunFile : String.format("%s/courgette-rerun.txt", reportTargetDir);
            }
            pluginList.add("rerun:" + rerunFile);
        }

        if (pluginList.stream().noneMatch(plugin -> plugin.contains(getCourgetteReportJson()))) {
            pluginList.add("json:" + getCourgetteReportJson());
        }

        if (courgetteProperties.isReportPortalPluginEnabled()) {
            if (pluginList.stream().noneMatch(plugin -> plugin.contains(getCourgetteReportXmlForReportPortal()))) {
                pluginList.add("junit:" + getCourgetteReportXmlForReportPortal());
            }
        }

        if (feature != null) {
            final String junitReportPlugin = String.format("junit:%s.xml", getMultiThreadReportFile());
            if (pluginList.stream().noneMatch(plugin -> plugin.equals(junitReportPlugin))) {
                pluginList.add(junitReportPlugin);
            }
        }

        return copyOf(pluginList.toArray(), pluginList.size(), String[].class);
    }

    private BiFunction<String, Object, List<String>> optionParser = (name, options) -> {
        final List<String> runOptions = new ArrayList<>();

        final Boolean isStringArray = options instanceof String[];

        if (options == null || (isStringArray && ((String[]) options).length == 0)) {
            return runOptions;
        }

        if (isStringArray) {
            final String[] optionArray = (String[]) options;

            asList(asList(optionArray).toString().split(","))
                    .forEach(value -> {
                        runOptions.add(name);
                        runOptions.add(value.trim().replace("[", "").replace("]", ""));
                    });
        } else {
            if (name != null) {
                runOptions.add(name);
            }
            runOptions.add(options.toString());
        }
        return runOptions;
    };

    private BiFunction<String[], String, List<String>> featureParser = (resourceFeaturePaths, featurePath) -> {
        final List<String> featurePaths = new ArrayList<>();
        if (featurePath == null) {
            featurePaths.addAll(Arrays.asList(resourceFeaturePaths));
        } else {
            featurePaths.add(featurePath);
        }
        return featurePaths;
    };

    private String getTempDirectory() {
        final String fileSeparator = File.separator;
        final String tmpDir = System.getProperty("java.io.tmpdir");

        if (!tmpDir.endsWith(fileSeparator)) {
            return tmpDir + fileSeparator;
        }
        return tmpDir;
    }
}