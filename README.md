# Data Capture
The `data-capture` service is responsible for the capture and ingestion of data from instruments in the SynBioChem centre. It is installed inside a Java servlet container (such as Tomcat) and it assumes that it is running on a system that has both the storage system (e.g., a NAS) and the instruments mounted as remote devices. The process of doing the mounting is outside the scope of this code.

## Building
This is a mavenized project; build by using `mvn package`. Builds produce two resulting artifacts:

1. `data-capture.war` — the WAR to deploy into the servlet container.
2. `data-capture-dropboxes.war` — the configuration of the dropboxes to deploy into OpenBIS.

## Workflow
The workflow implemented on upload is described in the `workflow()` method of the `manchester.synbiochem.datacapture.ArchiverTask` class. At a high level, it does this in this order:

1. _Lists_ the files to archive.
2. _Copies_ the files to the working store.
3. _Ingests_ the files into OpenBIS (via a second copy to a relevant drop-box).
4. _Registers_ an assay in SEEK for the ingestion if an existing assay was not in use.
5. _Computes_ the basic technical metadata for the files.
6. _Bags-up_ the data for archive. (Not yet implemented.)
7. _Stores_ the metadata in SEEK and on disk.

