package net.vulkanmod.render;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.vulkanmod.render.chunk.build.thread.ThreadBuilderPack;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

import java.util.List;

/**
 * 1.8.9-compatible version of PipelineManager.
 *
 * Notes:
 * - Replaces invokedynamic/lambda-metafactory usage with an explicit interface + anonymous class.
 * - Replaces newer mapped classes with 1.8.9 equivalents where possible.
 */
public abstract class PipelineManager {
    static GraphicsPipeline terrainShader;
    static GraphicsPipeline terrainShaderEarlyZ;
    static GraphicsPipeline fastBlitPipeline;
    static GraphicsPipeline cloudsPipeline;

    public static VertexFormat terrainVertexFormat;

    private static TerrainShaderGetter shaderGetter;

    private interface TerrainShaderGetter {
        GraphicsPipeline get(TerrainRenderType type);
    }

    public static void setTerrainVertexFormat(VertexFormat vertexFormat) {
        terrainVertexFormat = vertexFormat;
    }

    public static void init() {
        setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
        createBasicPipelines();
        setDefaultShader();
        ThreadBuilderPack.defaultTerrainBuilderConstructor();
    }

    public static void setDefaultShader() {
        // 1.8.9-safe callback setup (no lambda metafactory)
        setShaderGetter(new TerrainShaderGetter() {
            @Override
            public GraphicsPipeline get(TerrainRenderType type) {
                return terrainShader;
            }
        });
    }

    private static void createBasicPipelines() {
        terrainShaderEarlyZ = createPipeline("terrain_earlyZ", terrainVertexFormat);
        terrainShader = createPipeline("terrain", terrainVertexFormat);
        fastBlitPipeline = createPipeline("blit", CustomVertexFormat.NONE);
        cloudsPipeline = createPipeline("clouds", DefaultVertexFormats.POSITION_TEX_COLOR);
    }

    private static GraphicsPipeline createPipeline(String name, VertexFormat vertexFormat) {
        Pipeline.Builder builder = new Pipeline.Builder(vertexFormat, name);

        String shaderPath = ShaderLoadUtil.resolveShaderPath("basic");
        JsonObject config = ShaderLoadUtil.getJsonConfig(shaderPath, name);
        builder.parseBindings(config);
        ShaderLoadUtil.loadShaders(builder, config, name, shaderPath);

        GraphicsPipeline pipeline = builder.createGraphicsPipeline();

        List<UBO> buffers = pipeline.getBuffers();
        for (UBO ubo : buffers) {
            ubo.setUseGlobalBuffer(true);
        }

        return pipeline;
    }

    public static GraphicsPipeline getTerrainShader(TerrainRenderType type) {
        return shaderGetter.get(type);
    }

    public static void setShaderGetter(TerrainShaderGetter getter) {
        shaderGetter = getter;
    }

    // Signature adjusted for 1.8.9's render layer enum.
    public static GraphicsPipeline getTerrainDirectShader(BlockRenderLayer renderLayer) {
        return terrainShader;
    }

    // Signature adjusted for 1.8.9's render layer enum.
    public static GraphicsPipeline getTerrainIndirectShader(BlockRenderLayer renderLayer) {
        return terrainShaderEarlyZ;
    }

    public static GraphicsPipeline getFastBlitPipeline() {
        return fastBlitPipeline;
    }

    public static GraphicsPipeline getCloudsPipeline() {
        return cloudsPipeline;
    }

    public static void destroyPipelines() {
        if (terrainShaderEarlyZ != null) terrainShaderEarlyZ.cleanUp();
        if (terrainShader != null) terrainShader.cleanUp();
        if (fastBlitPipeline != null) fastBlitPipeline.cleanUp();
        if (cloudsPipeline != null) cloudsPipeline.cleanUp();
    }
}
