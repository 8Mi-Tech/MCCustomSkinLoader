package customskinloader.forge;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import customskinloader.Logger;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import customskinloader.forge.transformer.*;
import customskinloader.forge.transformer.PlayerTabTransformer.ScoreObjectiveTransformer;
import customskinloader.forge.transformer.SkinManagerTransformer.*;
import customskinloader.forge.transformer.SpectatorMenuTransformer.PlayerMenuObjectTransformer;

public class TransformerManager implements IClassTransformer {
    public static Logger logger = new Logger(new File(Launch.minecraftHome, "./CustomSkinLoader/ForgePlugin.log"));
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface TransformTarget{
        String className();
        String[] methodNames();
        String desc();
    }
    public interface IMethodTransformer{
        void transform(ClassNode cn,MethodNode mn);
    }
    private static final IMethodTransformer[] TRANFORMERS={
            new InitTransformer(),
            new LoadSkinTransformer(),
            new LoadProfileTexturesTransformer(),
            new LoadSkinFromCacheTransformer(),
            new ScoreObjectiveTransformer(),
            new PlayerMenuObjectTransformer(),
            new FakeSkinManagerTransformer.InitTransformer()
    };
    private Map<String, Map<String, IMethodTransformer>> map;
    public TransformerManager(){
        map = new HashMap<String, Map<String, IMethodTransformer>>();
        for(IMethodTransformer t:TRANFORMERS){
            logger.info("[CSL DEBUG] REGISTERING TRANSFORMER %s" , t.getClass().getName());
            if(!t.getClass().isAnnotationPresent(TransformTarget.class)){
                logger.info("[CSL DEBUG] ERROR occurs while parsing Annotation.");
                continue;
            }
            addMethodTransformer(t.getClass().getAnnotation(TransformTarget.class),t);
        }
    }
    private void addMethodTransformer(TransformTarget target, IMethodTransformer transformer) {
        if (!map.containsKey(target.className()))
            map.put(target.className(), new HashMap<String, IMethodTransformer>());
        for(String methodName:target.methodNames()){
            map.get(target.className()).put(methodName + target.desc(), transformer);
            logger.info("[CSL DEBUG] REGISTERING METHOD %s(%s)",target.className(),methodName + target.desc());
        }
    }
    
    //From: https://github.com/RecursiveG/UniSkinMod/blob/1.9.4/src/main/java/org/devinprogress/uniskinmod/coremod/BaseAsmTransformer.java#L83-L114
    @Override
    public byte[] transform(String obfClassName, String className, byte[] bytes) {
        if (!map.containsKey(className)) return bytes;
        logger.info("[CSL DEBUG] CLASS %s will be transformed", className);
        Map<String, IMethodTransformer> transMap = map.get(className);
        
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // NOTE: `map` = convert obfuscated name to srgName;
        List<MethodNode> ml = new ArrayList<MethodNode>(cn.methods);
        for (MethodNode mn : ml) {
            String methodName = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(obfClassName, mn.name, mn.desc);
            String methodDesc = FMLDeobfuscatingRemapper.INSTANCE.mapMethodDesc(mn.desc);
            if (transMap.containsKey(methodName + methodDesc)) {
                try {
                    logger.info("[CSL DEBUG] Transforming method %s in class %s(%s)", methodName + methodDesc, obfClassName, className);
                    transMap.get(methodName + methodDesc).transform(cn,mn);
                    logger.info("[CSL DEBUG] Successfully transformed method %s in class %s(%s)", methodName + methodDesc, obfClassName, className);
                } catch (Exception e) {
                    logger.warning("[CSL DEBUG] An error happened when transforming method %s in class %s(%s). The whole class was not modified.", methodName + methodDesc, obfClassName, className);
                    e.printStackTrace();
                    return bytes;
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
