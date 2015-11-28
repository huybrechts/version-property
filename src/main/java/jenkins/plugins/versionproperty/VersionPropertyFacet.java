package jenkins.plugins.versionproperty;

import hudson.model.Fingerprint;
import jenkins.model.FingerprintFacet;

public class VersionPropertyFacet extends FingerprintFacet {

    private final String property, version;

    public VersionPropertyFacet(Fingerprint fingerprint, long timestamp, String property, String version) {
        super(fingerprint, System.currentTimeMillis());
        this.property = property;
        this.version = version;
    }

    public String getProperty() {
        return property;
    }

    public String getVersion() {
        return version;
    }
}
