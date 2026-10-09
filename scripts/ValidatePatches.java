import app.revanced.patcher.patch.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Integration checks execute the real patches on a reduced fixture from the supplied APK. */
public final class ValidatePatches {
    private static final String FEED = "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;";
    private static final String HELPER = "Lapp/revanced/tiktok/FeedFilter;";
    private static final String ACL = "Lcom/ss/android/ugc/aweme/feed/model/ACLCommonShare;";
    private static final String DOWNLOADS = "Lcom/ss/android/ugc/aweme/feed/model/AwemeACLShare;";
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        File dir = new File(args[2]);
        dir.mkdirs();
        Set<String> wanted = new HashSet<>(Arrays.asList(
            FEED, ACL, DOWNLOADS, "LX/19k8;", "Lcom/ss/android/ugc/aweme/feed/model/Aweme;", "Lcom/ss/android/ugc/aweme/feed/model/AnchorCommonStruct;",
            "Lcom/ss/android/ugc/aweme/feed/FeedApiService;", "Lcom/ss/android/ugc/aweme/feed/api/FeedApi;",
            "Lcom/ss/android/ugc/tiktok/ConvertHelper;",
            "Lcom/ss/android/ugc/aweme/feed/model/live/NewLiveRoomStruct;",
            "Lcom/ss/android/ugc/aweme/feed/model/live/LiveRoomStruct;",
            "Lcom/ss/android/ugc/aweme/feed/model/live/RoomFeedCellStruct;",
            "Lcom/ss/android/ugc/aweme/feed/model/live/FYPCommerceStruct;",
            "Lcom/ss/android/ugc/aweme/feed/model/live/FeedRoomTag;",
            "Lcom/ss/android/ugc/aweme/feed/model/live/FeedRoomTagList;",
            "Lcom/ss/android/ugc/aweme/commerce/AwemeCommerceStruct;",
            "Lcom/ss/android/ugc/aweme/gsonopt/OptJsonAdapterFor$com$ss$android$ugc$aweme$feed$model$FeedItemList;"
        ));
        DexPool pool = new DexPool(Opcodes.getDefault());
        try (ZipFile zip = new ZipFile(args[0])) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                var dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(zip.getInputStream(entry)));
                for (ClassDef cls : dex.getClasses()) if (wanted.remove(cls.getType())) pool.internClass(cls);
            }
        }
        check(wanted.isEmpty(), "Fixture classes absent: " + wanted);
        File dexFile = new File(dir, "classes.dex");
        var store = new com.android.tools.smali.dexlib2.writer.io.FileDataStore(dexFile);
        try { pool.writeTo(store); } finally { store.close(); }
        File fixture = new File(dir, "fixture.apk");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(fixture))) {
            out.putNextEntry(new ZipEntry("classes.dex"));
            Files.copy(dexFile.toPath(), out);
            out.closeEntry();
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{new File(args[1]).toURI().toURL()}, ValidatePatches.class.getClassLoader())) {
            Class<?> specs = loader.loadClass("app.revanced.tiktok.patches.TikTokPatches");
            Patch ads = (Patch) specs.getField("hideAdsPatch").get(null);
            Patch shop = (Patch) specs.getField("hideShopVideosPatch").get(null);
            Patch registration = (Patch) loader.loadClass("app.revanced.tiktok.patches.RegistrationIdentityPatch").getField("fixDeviceRegistrationPatch").get(null);
            Patch packageName = (Patch) loader.loadClass("app.revanced.tiktok.patches.AlongsidePatch").getField("changePackageNamePatch").get(null);
            Patch downloads = (Patch) loader.loadClass("app.revanced.tiktok.patches.EnableDownloadsPatch").getField("enableDownloadsPatch").get(null);
            check(packageName.getDependencies().contains(registration), "Package change must depend on registration repair");
            for (Patch[] selection : new Patch[][]{{ads}, {shop}, {ads, shop}, {shop, ads}, {downloads}, {ads, shop, downloads}}) {
                BytecodePatchContext context = new BytecodePatchContext(fixture, new File(dir, "patched"));
                ResourcePatchContext resources = new ResourcePatchContext(new File(args[0]),
                    new File(dir, "resources"), new File(dir, "patched-resources"), null, null);
                context.getClassDefs().initializeCache$patcher();
                for (Patch dependency : ads.getDependencies()) dependency.getApply$patcher().invoke(context, resources);
                String before = render(context.getClassDefs().get(HELPER));
                String registrationBefore = render(context.getClassDefs().get("Lapp/revanced/tiktok/RegistrationIdentity;"));
                String permissionsBefore = render(context.getClassDefs().get(ACL));
                String otherSharingBefore = renderOtherSharing(context.getClassDefs().get(DOWNLOADS));
                for (Patch patch : selection) patch.getApply$patcher().invoke(context, resources);
                check(before.equals(render(context.getClassDefs().get(HELPER))), "A patch modified its runtime helper");
                check(registrationBefore.equals(render(context.getClassDefs().get("Lapp/revanced/tiktok/RegistrationIdentity;"))), "A patch modified the registration runtime helper");
                check(permissionsBefore.equals(render(context.getClassDefs().get(ACL))), "Global sharing permission accessors changed");
                check(otherSharingBefore.equals(renderOtherSharing(context.getClassDefs().get(DOWNLOADS))), "Unrelated sharing methods changed");
                if (Arrays.asList(selection).contains(downloads)) {
                    int branches = 0;
                    for (Method method : context.getClassDefs().get(DOWNLOADS).getMethods()) {
                        if (!method.getName().matches("getDownload(General|MaskPanel|SharePanel)")) continue;
                        for (var instruction : method.getImplementation().getInstructions()) {
                            if (!(instruction instanceof BuilderOffsetInstruction)) continue;
                            var target = ((BuilderOffsetInstruction) instruction).getTarget().getLocation().getInstruction();
                            check(instruction.getOpcode() == Opcode.IF_EQZ && target != null
                                && target.getOpcode() == Opcode.RETURN_OBJECT
                                && ((OneRegisterInstruction) target).getRegisterA() == 0,
                                "Missing download records must return null without a permission write");
                            branches++;
                        }
                    }
                    check(branches == 3, "Expected three guarded download getters");
                }
                boolean withFilter = Arrays.asList(selection).contains(ads) || Arrays.asList(selection).contains(shop);
                ClassDef feed = context.getClassDefs().get(FEED);
                for (Method method : feed.getMethods()) {
                    if (!method.getName().equals("getItems") || !withFilter) continue;
                    List<Instruction> instructions = new ArrayList<>();
                    method.getImplementation().getInstructions().forEach(instructions::add);
                    for (int i = 0; i < instructions.size(); i++) {
                        Instruction ins = instructions.get(i);
                        if (ins.getOpcode() == Opcode.RETURN_OBJECT) {
                            check(i >= 2 && instructions.get(i - 1).getOpcode() == Opcode.MOVE_RESULT_OBJECT,
                                "Unfiltered getter return");
                            String ref = ((ReferenceInstruction) instructions.get(i - 2)).getReference().toString();
                            check(ref.startsWith(HELPER), "Return filter references wrong helper");
                        }
                        if (ins instanceof BuilderOffsetInstruction) {
                            var target = ((BuilderOffsetInstruction) ins).getTarget().getLocation().getInstruction();
                            check(target == null || target.getOpcode() != Opcode.RETURN_OBJECT,
                                "Branch skips the inserted getter filter");
                        }
                    }
                }
                boolean withAds = Arrays.asList(selection).contains(ads);
                int cleared = 0;
                for (ClassDef cls : context.getClassDefs()) {
                    if (cls.getType().equals(HELPER)) continue;
                    for (Method method : cls.getMethods()) {
                        if (method.getImplementation() == null) continue;
                        List<Instruction> instructions = new ArrayList<>();
                        method.getImplementation().getInstructions().forEach(instructions::add);
                        for (int i = 0; i < instructions.size(); i++) {
                            var ins = instructions.get(i);
                            if (ins.getOpcode() != Opcode.IPUT_OBJECT) continue;
                            var ref = ((ReferenceInstruction) ins).getReference();
                            if (!ref.toString().equals(FEED + "->preloadAds:Ljava/util/List;")) continue;
                            boolean hasClear = i + 1 < instructions.size() && instructions.get(i + 1) instanceof ReferenceInstruction
                                && ((ReferenceInstruction) instructions.get(i + 1)).getReference().toString().equals(HELPER + "->clearPreloadAds(" + FEED + ")V");
                            check(hasClear == withAds, "Incorrect preload-write selection behavior");
                            if (hasClear) cleared++;
                        }
                    }
                }
                check(!withAds || cleared == 4, "Expected exactly four original preload writers");
                System.out.println("Bytecode integration passed: " + Arrays.toString(selection));
            }
        }
    }
    private static String render(ClassDef cls) {
        if (cls == null) return "";
        StringBuilder result = new StringBuilder();
        for (Method method : cls.getMethods()) {
            result.append(method);
            if (method.getImplementation() == null) continue;
            for (var ins : method.getImplementation().getInstructions()) {
                result.append(ins.getOpcode());
                if (ins instanceof ReferenceInstruction) result.append(((ReferenceInstruction) ins).getReference());
            }
        }
        return result.toString();
    }
    private static String renderOtherSharing(ClassDef cls) {
        StringBuilder result = new StringBuilder();
        for (Method method : cls.getMethods()) {
            if (method.getName().matches("getDownload(General|MaskPanel|SharePanel)")) continue;
            result.append(method);
            if (method.getImplementation() == null) continue;
            for (var ins : method.getImplementation().getInstructions()) {
                result.append(ins.getOpcode());
                if (ins instanceof ReferenceInstruction) result.append(((ReferenceInstruction) ins).getReference());
                if (ins instanceof WideLiteralInstruction) result.append(((WideLiteralInstruction) ins).getWideLiteral());
            }
        }
        return result.toString();
    }
}
