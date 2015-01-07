package org.wildfly.alpn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A utility to get the correct ALPN version for the current JVM version
 *
 * @author Stuart Douglas
 */
public class AlpnDownloader {


    public static String VERSIONS_URL = System.getProperty("versions.url", "http://undertow.io/alpn.txt");
    public static String MAVEN_REPO_URL = System.getProperty("maven.url", "http://repo1.maven.org/maven2/");

    public static void main(String[] args) throws Exception {
        if(args.length != 1) {
            System.err.println("USAGE: java -jar wildfly-alpn.jar wildfly.home");
            System.exit(1);
        }
        String wildflyHome = args[0];

        String jdkVersion = System.getProperty("java.version");
        File alpnBaseDir = new File(wildflyHome + File.separator + "bin" + File.separator + "alpn");
        if(!alpnBaseDir.exists()) {
            alpnBaseDir.mkdir();
        }

        File versionsFile = new File(wildflyHome + File.separator + "bin" + File.separator + "alpn" + File.separator + "versions.properties");
        Map<String, String> known = readVersions(versionsFile);
        String versionArtifact = known.get(jdkVersion);
        if(versionArtifact == null) {
            downloadFile(new URL(VERSIONS_URL), versionsFile);
            known = readVersions(versionsFile);
            versionArtifact = known.get(jdkVersion);
            if(versionArtifact == null) {
                System.err.println("No exact ALPN jar found for JDK version " + jdkVersion + " attempting to guess correct version. This may result in SSL errors if an incompatible version is used.");
                versionArtifact = guessVersion(known, jdkVersion);
                if(versionArtifact == null) {
                    System.err.println("Could not determine ALPN jar for JDK version " + jdkVersion);
                    System.exit(1);
                }
            }
        }
        String[] parts = versionArtifact.split(":");
        String groupId = parts[0];
        String artifactId = parts[1];
        String version =  parts[2] ;
        String classifier = parts.length == 4 ? parts[3] : null;

        String mavenPath = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + (classifier == null ? "" : "-" + classifier) + ".jar";
        File artifactFile = new File(alpnBaseDir + File.separator + mavenPath.replace('/', File.separatorChar));
        if(!artifactFile.exists()) {
            artifactFile.getParentFile().mkdirs();
            downloadFile(new URL(MAVEN_REPO_URL + mavenPath), artifactFile);
        }
        System.out.println(artifactFile.getAbsolutePath());
    }

    private static String guessVersion(Map<String, String> known, String version) {
        String[] split = version.split("_");
        String major = split[0];
        int minor = Integer.parseInt(split[1]);
        String current = null;
        int currentMinor = -1;
        for(Map.Entry<String, String> entry : known.entrySet()) {
            split = entry.getKey().split("_");
            String ma = split[0];
            int mi;
            if(split.length == 1) {
                mi = 0;
            } else {
                mi = Integer.parseInt(split[1]);
            }
            if(!ma.equals(major)) {
                continue;
            }
            if(minor > mi && mi > currentMinor) {
                currentMinor = mi;
                current = entry.getValue();
            }
        }
        return current;
    }

    private static void downloadFile(URL url, File file) throws Exception {
        try (InputStream stream = url.openStream(); OutputStream out = new FileOutputStream(file)){
            byte[] data = new byte[1024];
            int r;
            while ((r = stream.read(data)) > 0) {
                out.write(data, 0 , r);
            }
            out.flush();
        }
    }

    private static Map<String, String> readVersions(File versionsFile) throws IOException {
        if(!versionsFile.exists()) {
            return Collections.emptyMap();
        }
        Properties p = new Properties();
        p.load(new FileInputStream(versionsFile));
        Map<String, String> ret = new HashMap<>();
        for(Map.Entry<Object, Object> entry : p.entrySet()) {
            ret.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return ret;
    }

}
