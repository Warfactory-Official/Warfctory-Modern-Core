package com.norwood.wfcore.client.render.gltf;

import net.minecraft.resources.ResourceLocation;

import com.google.common.collect.ImmutableMap;
import com.modularmods.mcgltf.IGltfModelReceiver;
import com.modularmods.mcgltf.RenderedGltfModel;
import com.modularmods.mcgltf.RenderedGltfScene;
import com.modularmods.mcgltf.animation.GltfAnimationCreator;
import de.javagl.jgltf.model.AnimationModel;

import java.util.List;
import java.util.Locale;

/**
 * Holds the GPU-ready GLTF scene and animation set for one model resource, shared across every block
 * entity that renders it. Ported from the 1.12.2 {@code MteRenderer}/{@code GenericGLTF} model-receipt
 * logic; registered with McGLTF via {@link com.modularmods.mcgltf.MCglTF#addGltfModelReceiver}.
 */
public class MachineGltfModel implements IGltfModelReceiver {

    private final ResourceLocation modelLocation;

    /** Loop map keyed by animation name; an animation named {@code <name>_loop} loops. */
    public ImmutableMap<String, AnimationLoop> animations;
    /** The preprocessed, GPU-ready scene, or null until the model is received on the client. */
    public RenderedGltfScene scene;

    public MachineGltfModel(ResourceLocation modelLocation) {
        this.modelLocation = modelLocation;
    }

    @Override
    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    @Override
    public void onReceiveSharedModel(RenderedGltfModel renderedModel) {
        this.scene = renderedModel.renderedGltfScenes.get(0);
        List<AnimationModel> animationModels = renderedModel.gltfModel.getAnimationModels();
        ImmutableMap.Builder<String, AnimationLoop> builder = ImmutableMap.builder();
        for (AnimationModel animationModel : animationModels) {
            AnimationLoop loop = new AnimationLoop(GltfAnimationCreator.createGltfAnimation(animationModel));
            String[] name = animationModel.getName().toLowerCase(Locale.ROOT).split("_");
            if (name.length > 1 && name[1].equals("loop")) {
                loop.setLoop(true);
            }
            builder.put(name[0], loop);
        }
        this.animations = builder.build();
    }
}
