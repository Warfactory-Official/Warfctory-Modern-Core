package com.norwood.wfcore.client.render.gltf;

import net.minecraft.resources.ResourceLocation;

import com.google.common.collect.ImmutableMap;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * One machine model, shared by every visual that draws it. Replaces the McGLTF
 * {@code IGltfModelReceiver}: declaring the handle in a {@code static final} is the whole registration,
 * and GemRender imports the asset at the end of a resource reload rather than handing it back through a
 * callback.
 * <p>
 * The handle is held rather than the model, because the model owns an atlas texture that a reload frees.
 * The clip map is rebuilt whenever the model behind the handle is replaced.
 */
public final class MachineModel {

    /** The model the clip map was built from, and the map, swapped together. */
    private record Clips(GemRenderGltfModel model, ImmutableMap<String, MachineClip> byName) {}

    private final ResourceLocation asset;
    private final ModelCache.Handle<GemRenderGltfModel> handle;

    /**
     * Read by Flywheel task threads and replaced by whichever gets there first after a reload. Both
     * halves swap at once, so a reader never sees clips belonging to a previous model.
     */
    private volatile Clips clips = new Clips(null, ImmutableMap.of());

    public MachineModel(ResourceLocation asset) {
        this.asset = asset;
        this.handle = GemRenderModels.handle(asset);
    }

    public ResourceLocation asset() {
        return asset;
    }

    @Nullable
    public GemRenderGltfModel model() {
        return handle.get();
    }

    /** The model's animations by wfcore state name, or empty until the asset has imported. */
    public Map<String, MachineClip> animations() {
        GemRenderGltfModel model = handle.get();
        Clips current = clips;
        if (current.model() == model) {
            return current.byName();
        }
        if (model == null) {
            clips = new Clips(null, ImmutableMap.of());
            return ImmutableMap.of();
        }

        ImmutableMap.Builder<String, MachineClip> builder = ImmutableMap.builder();
        for (GltfAnimation clip : model.animations().values()) {
            String[] name = clip.name().toLowerCase(Locale.ROOT).split("_");
            builder.put(name[0], new MachineClip(clip, name.length > 1 && name[1].equals("loop")));
        }

        Clips built = new Clips(model, builder.buildKeepingLast());
        clips = built;
        return built.byName();
    }
}
