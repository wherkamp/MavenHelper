package me.kingtux.mvnhelper.maven;

import me.kingtux.mvnhelper.MavenHelper;
import okhttp3.Request;
import okhttp3.Response;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MavenResolver {
    private List<Repository> repositoryList;

    public MavenResolver(List<Repository> repositoryList) {
        this.repositoryList = repositoryList;
        for (Repository repository : repositoryList) {
            MavenHelper.LOGGER.debug("repository = " + repository);
        }
    }

    public Optional<Artifact> artifact(String groupID, String artifactId) {
        for (Repository repository : repositoryList) {
            Optional<Artifact> artifact = artifact(groupID, artifactId, repository);
            if (artifact.isPresent()) return artifact;
        }
        return Optional.empty();
    }

    public Optional<Artifact> artifact(String groupID, String artifactId, Repository repository) {
        return artifact(new ArtifactRequest(groupID, artifactId, repository));

    }

    public Optional<Artifact> artifact(ArtifactRequest artifactRequest) {
        Document mavenMetadata = null;
        try {
            mavenMetadata = getMavenMetadata(artifactRequest.getGroupID(), artifactRequest.getArtifactID(), artifactRequest.getRepository());
        } catch (DocumentException | IOException e) {
            MavenHelper.LOGGER.error("Unable to pull maven-metadata.xml", e);
            return Optional.empty();
        }
        if (mavenMetadata == null) return Optional.empty();
        ArtifactBuilder artifactBuilder = new ArtifactBuilder();
        artifactBuilder.setArtifactId(artifactRequest.getArtifactID());
        artifactBuilder.setGroupId(artifactRequest.getGroupID());
        artifactBuilder.setRepository(artifactRequest.getRepository());
        List<String> versions = new ArrayList<>();
        Element root = mavenMetadata.getRootElement();
        Element versioning = root.element("versioning");
        Element latest = versioning.element("latest");
        if (latest != null) {
            artifactBuilder.setLatestVersion(latest.getStringValue());
        }
        if (latest == null) {
            Element release = versioning.element("release");
            if (release != null) {
                artifactBuilder.setLatestVersion(release.getStringValue());
            }
        }
        Element versionsElement = versioning.element("versions");
        for (Element element : versionsElement.elements()) {
            versions.add(element.getStringValue());
        }
        artifactBuilder.setVersions(versions);
        return Optional.of(artifactBuilder.createArtifact());

    }

    public Optional<Repository> getRepository(String id) {
        return repositoryList.stream().filter(repository -> repository.getRepositoryID().equalsIgnoreCase(id)).findFirst();
    }

    private Document getMavenMetadata(String groupID, String artifactId, Repository repository) throws DocumentException, IOException {
        String url = getArtifactURL(groupID, artifactId, repository) + "/maven-metadata.xml";
        MavenHelper.LOGGER.debug("Maven-METADATA.xml URL: " + url);
        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response execute = MavenHelper.CLIENT.newCall(request).execute()) {
            if (execute.code() == 404) return null;
            SAXReader reader = new SAXReader();
            Document doc = reader.read(execute.body().byteStream());
            return doc;
        } catch (IOException e) {
            throw e;
        }
    }

    private String getArtifactURL(String groupID, String artifactId, Repository repository) {
        StringBuilder stringBuilder = new StringBuilder().append(repository.getURL()).append("/");
        stringBuilder.append(groupID.replace(".", "/")).append("/");
        stringBuilder.append(artifactId);

        return stringBuilder.toString();
    }

    public List<Repository> getRepositoryList() {
        return repositoryList;
    }
}
