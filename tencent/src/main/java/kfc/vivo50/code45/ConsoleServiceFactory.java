package kfc.vivo50.code45;

import kotlinx.coroutines.CoroutineScope;
import net.mamoe.mirai.internal.spi.EncryptServiceContext;
import org.jetbrains.annotations.NotNull;
import tencentlibfekit.BasePath;
import tencentlibfekit.FEKitEncryptService;
import tencentlibfekit.FEKitNativeIOFiles;
import tencentlibfekit.structloader.ExtForceLoadStructLoader;
import tencentlibfekit.structloader.LoadedClassLoaderStructLoader;
import tencentlibfekit.structloader.StructLoader;
import tencentlibfekit.vmservice.IVMService;
import tencentlibfekit.vmservice.VMService;
import tencentlibfekit.vmservice.rpc.RpcClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public abstract class ConsoleServiceFactory implements FEKitEncryptService.ServiceLoader {

    public abstract IVMService load(@NotNull EncryptServiceContext context, @NotNull CoroutineScope serviceSubScope) throws Throwable;


    protected abstract static class Local extends ConsoleServiceFactory {
        private final File apkFile;
        private final File libFekit;

        public Local(File apkFile, File libFekit) {
            this.apkFile = apkFile;
            this.libFekit = libFekit;
        }

        @Override
        public IVMService load(@NotNull EncryptServiceContext context, @NotNull CoroutineScope serviceSubScope) throws Throwable {
            var dataFolder = Code45.INSTANCE.getDataFolder();

            var wk = new File(dataFolder, String.valueOf(context.getId()));

            var nativeIOFiles = new FEKitNativeIOFiles();
            nativeIOFiles.apkFile = apkFile;
            nativeIOFiles.soFile = libFekit;
            nativeIOFiles.userData = new File(wk, "userPath");
            initRemaining(nativeIOFiles);

            wk.mkdirs();


            var service = new VMService(
                    wk,
                    getStructLoader(),
                    nativeIOFiles, true, context.getId()
            );

            // new ExtForceLoadStructLoader(new File(BasePath.STRUCT_DEF), true)
            return service;
        }


        protected abstract StructLoader getStructLoader() throws Throwable;

        protected abstract void initRemaining(FEKitNativeIOFiles nativeIOFiles) throws Throwable;

    }

    static class LocalTest extends Local {
        public LocalTest(File apkFile, File libFekit) {
            super(apkFile, libFekit);
        }

        @Override
        protected StructLoader getStructLoader() throws IOException {
            return new ExtForceLoadStructLoader(new File(BasePath.STRUCT_DEF), true);
        }

        @Override
        protected void initRemaining(FEKitNativeIOFiles nativeIOFiles) {
            nativeIOFiles.linuxFiles = new File(BasePath.PROJECT_DIR, "linuxfile");
        }
    }

    static class Packed extends Local {
        public Packed(File apkFile, File libFekit) {
            super(apkFile, libFekit);
        }

        @Override
        protected StructLoader getStructLoader() throws IOException {
            return new LoadedClassLoaderStructLoader(getClass().getClassLoader(), ServiceSelector.mainClass());
        }

        @Override
        protected void initRemaining(FEKitNativeIOFiles nativeIOFiles) {
            nativeIOFiles.linuxFiles = new File(Code45.unpackResources(), "linuxfile");
        }
    }

    static class Remote extends ConsoleServiceFactory {
        private final URI mBaseUrl;
        private final String mAuthKey;
        private final String mServerIdentityKey;

        public Remote(URI baseUrl, String authKey, String serverIdentityKey) {
            this.mBaseUrl = baseUrl;
            this.mAuthKey = authKey;
            this.mServerIdentityKey = serverIdentityKey;
        }

        @Override
        public IVMService load(@NotNull EncryptServiceContext context, @NotNull CoroutineScope serviceSubScope) throws Throwable {
            return new RpcClient(mBaseUrl, context, serviceSubScope, mAuthKey, mServerIdentityKey);
        }
    }
}


