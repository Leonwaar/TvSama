package fr.nekotv.downloads;

import java.io.InputStream;

public interface DownloadHandler {
    String getAllJobs();
    String getJob(String id);
    String createJob(InputStream body);
    String updateJob(String id, InputStream body);
    String deleteJob(String id);
    String getLibrary();
}
