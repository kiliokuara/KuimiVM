package tencentlibfekit.common;

import kfc.vivo50.code45.ServiceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tencentlibfekit.proto.QVersionConst;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static tencentlibfekit.common.GlobalShared.HTTP_CLIENT;

public class ResourceExtract {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceExtract.class);

    public static File unpackResources(File dataFolder) {
        var ltoken = ServiceSelector.resourceSha1();
        if (ltoken.isEmpty()) {
            throw new IllegalStateException("Unpacked service");
        }

        var folder = new File(dataFolder, "resources");
        var lhash = new File(folder, "hash.txt");

        if (lhash.isFile()) {
            try {
                var content = Files.readString(lhash.toPath());
                if (content.equals(ltoken)) {
                    return folder;
                }
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        //noinspection DataFlowIssue
        try (var zipInput = new ZipInputStream(ResourceExtract.class.getResourceAsStream("/kfc/vivo50/code45/resources.zip"))) {
            while (true) {
                var entry = zipInput.getNextEntry();
                if (entry == null) break;
                if (entry.isDirectory()) continue;

                var targetFile = new File(folder, entry.getName());
                targetFile.getParentFile().mkdirs();

                LOGGER.info("extracting {}", entry.getName());
                Files.copy(zipInput, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            Files.writeString(lhash.toPath(), ltoken);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        return folder;
    }

    public static ApkResult downloadMobileQQ(File dataFolder) throws IOException, NoSuchAlgorithmException, InterruptedException {
        dataFolder.mkdirs();
        var apkFile = new File(dataFolder, "android-" + QVersionConst.MAIN_VERSION + ".apk");

        if (!checkSha1(apkFile)) {
            try {
                apkFile.delete();
            } catch (Throwable t) {
            }
            LOGGER.info("[HTTP GET] downloading {}", QVersionConst.DOWNLOAD_URL);
            var rsp = HTTP_CLIENT.send(
                    HttpRequest.newBuilder().GET().uri(URI.create(QVersionConst.DOWNLOAD_URL)).build(),
                    HttpResponse.BodyHandlers.ofFile(apkFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
            );
            if (rsp.statusCode() != 200) {
                throw new IllegalStateException("Failed to download android" + QVersionConst.MAIN_VERSION + ", please download it manually: " + QVersionConst.DOWNLOAD_URL);
            }

            if (!checkSha1(apkFile)) {
                throw new IllegalStateException("downloaded mobile qq apk sha1 doesn't match " + QVersionConst.SHA1 + ", please download manually: " + QVersionConst.DOWNLOAD_URL);
            }
        }

        // region unzip libfekit.so
        var libFekit = new File(dataFolder, "libfekit.so");
        LOGGER.info("extracting libfekit.so");

        check:
        {
            var counter = 0;

            while (true) {
                if (!libFekit.isFile()) {
                    try (var zipFile = new ZipFile(apkFile)) {
                        Files.write(libFekit.toPath(), zipFile.getInputStream(zipFile.getEntry("lib/arm64-v8a/libfekit.so")).readAllBytes());
                    }
                }

                {
                    var sha1 = HexFormat.of().withLowerCase().formatHex(sha1(Files.readAllBytes(libFekit.toPath())));
                    if (sha1.equals(QVersionConst.SHA1_FEKIT)) {
                        break check;
                    } else {
                        libFekit.delete();
                    }
                }

                if (counter++ == 4) {
                    throw new IllegalStateException("Failed to write libfekit.so: sha1 not match. Excepted " + QVersionConst.SHA1_FEKIT);
                }
            }

        }
        // endregion

        if (!ServiceSelector.isLocal()) {
            unpackResources(dataFolder);
        }

        return new ApkResult(apkFile, libFekit);
    }

    public record ApkResult(
            File apk,
            File fekit
    ) {
    }


    private static byte[] sha1(byte[] data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA1").digest(data);
    }

    private static boolean checkSha1(File apk) throws IOException, NoSuchAlgorithmException {
        if (!apk.exists()) return false;
        LOGGER.info("checking sha1 of apk {}", apk);

        var sha1 = HexFormat.of().formatHex(sha1(Files.newInputStream(apk.toPath())));
        return sha1.equals(QVersionConst.SHA1);
    }

    private static byte[] sha1(InputStream resource) throws NoSuchAlgorithmException, IOException {
        try (resource) {
            var md = MessageDigest.getInstance("SHA1");
            resource.transferTo(new MessageDigestAsOutputStream(md));
            return md.digest();
        }
    }
}
