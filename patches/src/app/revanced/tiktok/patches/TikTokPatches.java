package app.revanced.tiktok.patches;

import app.revanced.patcher.patch.BytecodePatchContext;
import app.revanced.patcher.patch.Patch;
import app.revanced.patcher.patch.PatchKt;
import app.revanced.patcher.extensions.InstructionExtensions;
import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kotlin.Unit;

/** ReVanced Patcher 22 API; Java keeps the build free of GitHub Maven credentials. */
public final class TikTokPatches {
    private static final String MODEL = "Lcom/ss/android/ugc/aweme/feed/model/";
    private static final String FEED = MODEL + "FeedItemList;";
    private static final String HELPER = "Lapp/revanced/tiktok/FeedFilter;";
    private static final InstructionExtensions INS = InstructionExtensions.INSTANCE;

    static final Patch EXTENSION = PatchKt.bytecodePatch(null, null, true, builder -> {
        // The String overload relies on Kotlin inlining to capture its caller's
        // classloader. Supply our own loader explicitly when calling from Java.
        builder.extendWith(() -> {
            var stream = TikTokPatches.class.getResourceAsStream("/extensions/tiktok.rve");
            if (stream == null) throw new IllegalStateException("Missing bundled TikTok extension");
            return stream;
        });
        return Unit.INSTANCE;
    });

    public static final Patch hideAdsPatch = create(false);
    public static final Patch hideShopVideosPatch = create(true);

    private static Patch create(boolean shop) {
        return PatchKt.bytecodePatch(
            shop ? "Hide Shop videos" : "Hide ads",
            shop ? "Removes product-linked videos and shopping live promotions from feeds."
                 : "Removes ads, paid partnerships and disclosed promotional content from feeds.",
            true,
            builder -> {
                builder.compatibleWith(builder.invoke("com.zhiliaoapp.musically", "47.1.4"));
                builder.dependsOn(EXTENSION);
                builder.apply(context -> {
                    patch(context, shop);
                    return Unit.INSTANCE;
                });
                return Unit.INSTANCE;
            }
        );
    }

    private static ClassDef requireClass(BytecodePatchContext context, String type) {
        ClassDef cls = context.getClassDefs().get(type);
        if (cls == null) throw new IllegalStateException("Missing verified TikTok class: " + type);
        return cls;
    }

    private static Method requireMethod(ClassDef cls, String name, String result) {
        for (Method method : cls.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().isEmpty()
                    && method.getReturnType().equals(result) && method.getImplementation() != null
                    && !AccessFlags.STATIC.isSet(method.getAccessFlags())) return method;
        }
        throw new IllegalStateException("Missing verified TikTok method: " + cls.getType() + "->" + name);
    }

    private static void requireField(ClassDef cls, String name, String type) {
        for (var field : cls.getFields()) {
            if (field.getName().equals(name) && field.getType().equals(type)
                    && AccessFlags.PUBLIC.isSet(field.getAccessFlags())
                    && !AccessFlags.STATIC.isSet(field.getAccessFlags())) return;
        }
        throw new IllegalStateException("Missing public TikTok field: " + cls.getType() + "->" + name);
    }

    private static MutableMethod mutable(BytecodePatchContext context, Method target) {
        for (MutableMethod method : context.proxy(requireClass(context, target.getDefiningClass()))
                .getMutableClass().getMethods()) {
            if (method.getName().equals(target.getName())
                    && method.getParameterTypes().equals(target.getParameterTypes())
                    && method.getReturnType().equals(target.getReturnType())) return method;
        }
        throw new IllegalStateException("Could not resolve mutable method: " + target);
    }

    private static void patch(BytecodePatchContext context, boolean shop) {
        ClassDef feed = requireClass(context, FEED);
        ClassDef aweme = requireClass(context, MODEL + "Aweme;");
        requireField(feed, "items", "Ljava/util/List;");
        requireField(aweme, "newLiveRoomData", MODEL + "live/NewLiveRoomStruct;");
        requireMethod(aweme, "getRoomFeedCellStruct", MODEL + "live/RoomFeedCellStruct;");
        ClassDef cell = requireClass(context, MODEL + "live/RoomFeedCellStruct;");
        requireField(cell, "newLiveRoomData", MODEL + "live/NewLiveRoomStruct;");
        requireField(cell, "room", MODEL + "live/LiveRoomStruct;");
        for (String room : new String[]{"NewLiveRoomStruct", "LiveRoomStruct"}) {
            requireField(requireClass(context, MODEL + "live/" + room + ";"), "feedRoomTagList", MODEL + "live/FeedRoomTagList;");
        }
        ClassDef tags = requireClass(context, MODEL + "live/FeedRoomTagList;");
        for (String field : new String[]{"firstTags", "subTags", "bottomTags", "bottomSubTags", "bcToggleTags", "boostToggleTags"}) {
            requireField(tags, field, "Ljava/util/List;");
        }
        ClassDef tag = requireClass(context, MODEL + "live/FeedRoomTag;");
        requireField(tag, "id", "J");
        requireField(tag, "content", "Ljava/lang/String;");
        if (shop) {
            requireMethod(aweme, "getProductsCount", "I");
            requireMethod(aweme, "getProductsInfo", "Ljava/util/List;");
            requireMethod(aweme, "getIsLiveHasProduct", "Z");
            requireMethod(aweme, "getAnchors", "Ljava/util/List;");
            requireMethod(requireClass(context, MODEL + "AnchorCommonStruct;"), "getType", "I");
            for (String room : new String[]{"NewLiveRoomStruct", "LiveRoomStruct"}) {
                ClassDef cls = requireClass(context, MODEL + "live/" + room + ";");
                requireField(cls, "hasCommerceGoods", "Z");
                requireField(cls, "fypCommerceStruct", MODEL + "live/FYPCommerceStruct;");
            }
            ClassDef commerce = requireClass(context, MODEL + "live/FYPCommerceStruct;");
            requireField(commerce, "productNum", "Ljava/lang/Long;");
            requireField(commerce, "popProductId", "Ljava/lang/Long;");
        } else {
            String commerce = "Lcom/ss/android/ugc/aweme/commerce/AwemeCommerceStruct;";
            requireMethod(aweme, "getCommerceVideoAuthInfo", commerce);
            requireMethod(requireClass(context, commerce), "isBrandedContent", "Z");
            requireMethod(requireClass(context, commerce), "isBrandOrganicContent", "Z");
            requireField(aweme, "_isAd", "Z");
            requireField(aweme, "_isSoftAd", "Z");
            requireField(feed, "preloadAds", "Ljava/util/List;");
        }

        // Both getAwemeList() and the interface path delegate to getItems().
        MutableMethod getter = mutable(context, requireMethod(feed, "getItems", "Ljava/util/List;"));
        int self = getter.getImplementation().getRegisterCount() - 1;
        String action = shop ? "shop" : "ads";
        List<Integer> returns = returnIndexes(getter);
        if (returns.isEmpty()) throw new IllegalStateException("getItems has no object return");
        for (int index : returns) {
            int result = ((OneRegisterInstruction) getter.getImplementation().getInstructions().get(index)).getRegisterA();
            if (self > 15 || result > 15) throw new IllegalStateException("Unexpected getItems register layout");
            INS.addInstructions(getter, index, "invoke-static {v" + self + ", v" + result + "}, "
                + HELPER + "->" + action + "(" + FEED + "Ljava/util/List;)Ljava/util/List;\n"
                + "move-result-object v" + result);
        }

        // Network and protobuf boundaries cover paths that read items directly.
        int boundaries = 0;
        String[] boundaryClasses = {
            "Lcom/ss/android/ugc/aweme/feed/FeedApiService;",
            "Lcom/ss/android/ugc/aweme/feed/api/FeedApi;",
            "Lcom/ss/android/ugc/tiktok/ConvertHelper;"
        };
        for (String type : boundaryClasses) {
            ClassDef cls = requireClass(context, type);
            int matched = 0;
            for (Method original : cls.getMethods()) {
                if (!original.getReturnType().equals(FEED) || original.getImplementation() == null) continue;
                MutableMethod method = mutable(context, original);
                for (int index : returnIndexes(method)) {
                    int register = ((OneRegisterInstruction) method.getImplementation().getInstructions().get(index)).getRegisterA();
                    INS.addInstruction(method, index, invokeOne(register, action + "Feed"));
                    matched++;
                }
            }
            if (matched == 0) throw new IllegalStateException("Missing feed boundary in " + type);
            boundaries += matched;
        }

        int preloadWrites = 0;
        if (!shop) {
            // Clear after writes without clobbering the source register, which may be reused.
            for (ClassDef cls : new ArrayList<>(context.getClassDefs())) {
                // Never instrument the helper's own clearPreloadAds write: that
                // would make the injected helper recursively call itself.
                if (cls.getType().startsWith("Lapp/revanced/tiktok/")) continue;
                for (Method original : cls.getMethods()) {
                    if (original.getImplementation() == null) continue;
                    List<Integer> writes = new ArrayList<>();
                    int index = 0;
                    for (Instruction ins : original.getImplementation().getInstructions()) {
                        if (ins.getOpcode() == Opcode.IPUT_OBJECT && ins instanceof ReferenceInstruction
                                && ((ReferenceInstruction) ins).getReference() instanceof FieldReference) {
                            FieldReference field = (FieldReference) ((ReferenceInstruction) ins).getReference();
                            if (field.getDefiningClass().equals(FEED) && field.getName().equals("preloadAds")
                                    && field.getType().equals("Ljava/util/List;")) writes.add(index);
                        }
                        index++;
                    }
                    if (writes.isEmpty()) continue;
                    MutableMethod method = mutable(context, original);
                    Collections.reverse(writes);
                    for (int write : writes) {
                        int owner = ((TwoRegisterInstruction) method.getImplementation().getInstructions().get(write)).getRegisterB();
                        INS.addInstruction(method, write + 1, invokeOne(owner, "clearPreloadAds"));
                        preloadWrites++;
                    }
                }
            }
            if (preloadWrites < 4) throw new IllegalStateException("Unexpected preloadAds write coverage: " + preloadWrites);
        }
        System.out.println(action + ": " + returns.size() + " getter returns, " + boundaries
            + " boundary returns, " + preloadWrites + " preload writes");
    }

    private static String invokeOne(int register, String method) {
        return "invoke-static/range {v" + register + " .. v" + register + "}, "
            + HELPER + "->" + method + "(" + FEED + ")V";
    }

    private static List<Integer> returnIndexes(MutableMethod method) {
        List<Integer> indexes = new ArrayList<>();
        List<? extends Instruction> instructions = method.getImplementation().getInstructions();
        for (int i = instructions.size() - 1; i >= 0; i--) {
            if (instructions.get(i).getOpcode() == Opcode.RETURN_OBJECT) indexes.add(i);
        }
        return indexes;
    }
}
