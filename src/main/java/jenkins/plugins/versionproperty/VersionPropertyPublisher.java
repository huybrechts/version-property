package jenkins.plugins.versionproperty;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.*;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionPropertyPublisher extends Recorder {

    private String property;
    private String pom;

    @DataBoundConstructor
    public VersionPropertyPublisher(String property, String pom) {
        this.property = Util.fixEmptyAndTrim(property);
        this.pom = Util.fixEmptyAndTrim(pom);

        if (property == null) throw new IllegalArgumentException("property is required");
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getProperty() {
        return property;
    }

    public String getPom() {
        return pom;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        String pom = this.pom != null ? this.pom : "pom.xml";

        //TODO look at Maven builder for finding pom

        FilePath pomFile = build.getWorkspace().child(pom);
        if (!pomFile.exists()) {
            listener.getLogger().print("[version-property] could not find pom at " + pom);
            return false;
        }

        try {
            Map<String,String> record = new HashMap<String,String>();

            Document doc = new SAXReader().read(pomFile.read());

            if (property != null) {
                String version = doc.getRootElement().elementText("version");
                if (version == null) {
                    version = doc.getRootElement().element("parent").elementText("version");
                }
                if (version.contains("SNAPSHOT")) {
                    version = findVersion(build.getLogFile());
                }

                addFingerprint(build, record, property, version);
            }

            Element properties = doc.getRootElement().element("properties");
            if (properties != null) {
                for (String propertyName: getPropertyNames()) {
                    String value = properties.elementTextTrim(propertyName);
                    if (value != null && !value.endsWith("SNAPSHOT")) {
                        addFingerprint(null, record, propertyName, value);
                    }
                }
            }

            Fingerprinter.FingerprintAction action = build.getAction(Fingerprinter.FingerprintAction.class);
            if (action != null) {
                action.add(record);
            } else  {
                build.addAction(new Fingerprinter.FingerprintAction(build, record));
            }

        } catch (Exception e) {
            e.printStackTrace(listener.error("[version-property] could not read pom at " + pom));
        }

        return true;
    }

    private static List<String> getPropertyNames() {
        List<String> propertyNames = new ArrayList<String>();
        for (AbstractProject<?,?> p: Hudson.getInstance().getItems(AbstractProject.class)) {
            VersionPropertyPublisher publisher = p.getPublishersList().get(VersionPropertyPublisher.class);
            if (publisher != null) {
                String property = publisher.getProperty();
                if (property != null) {
                    propertyNames.add(property);
                }
            }
        }
        return propertyNames;
    }

    private void addFingerprint(AbstractBuild<?, ?> build, Map<String, String> record, String property, String value) throws IOException {
        String md5sum = Util.getDigestOf(property + value);
        Fingerprint fp = Hudson.getInstance().getFingerprintMap().getOrCreate(build, "version", md5sum);
        fp.add(build);
        record.put(property, md5sum);
    }

    private String findVersion(File logFile) throws IOException,
            InterruptedException {
        return doLogSearch(
                logFile,
                "\\[INFO\\] Uploading project information for [^\\s]* ([^\\s]*)",
                "\\[INFO\\] Updating \\S* to (\\d\\S*)",
                "Building .*? (\\d\\S*)"
        );
    }

    private String doLogSearch(File logFile, String... p)
            throws FileNotFoundException, IOException {
        List<Pattern> patterns = new ArrayList<Pattern>();
        for (String pp: p) patterns.add(Pattern.compile(pp));

        // Assume default encoding and text files
        String line;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(logFile));
            while ((line = reader.readLine()) != null) {
                for (Pattern pattern: patterns) {
                    Matcher matcher = pattern.matcher(line);
                    String version = matcher.group(1);
                    if (matcher.find() && !version.contains("SNAPSHOT")) {
                        return version;
                    }
                }
            }
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Extension(ordinal = -1)
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return "Version property fingerprinter";
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return FreeStyleProject.class.isAssignableFrom(aClass);
        }
    }
}
