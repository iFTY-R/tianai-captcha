package cloud.tianai.captcha.generator.impl;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.generator.AbstractImageCaptchaGenerator;
import cloud.tianai.captcha.generator.ImageTransform;
import cloud.tianai.captcha.generator.common.constant.SliderCaptchaConstant;
import cloud.tianai.captcha.generator.common.model.dto.GenerateParam;
import cloud.tianai.captcha.generator.common.model.dto.ImageCaptchaInfo;
import cloud.tianai.captcha.generator.common.model.dto.ImageTransformData;
import cloud.tianai.captcha.generator.common.model.dto.SliderImageCaptchaInfo;
import cloud.tianai.captcha.generator.common.util.CaptchaImageUtils;
import cloud.tianai.captcha.resource.ImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.common.model.dto.ResourceMap;
import cloud.tianai.captcha.resource.impl.provider.ClassPathResourceProvider;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static cloud.tianai.captcha.common.constant.CommonConstant.DEFAULT_TAG;

/**
 * @Author: 天爱有情
 * @Date 2020/5/29 8:06
 * @Description 滑块验证码模板
 */
@Slf4j
public class StandardSliderImageCaptchaGenerator extends AbstractImageCaptchaGenerator {

    /**
     * 默认的resource资源文件路径.
     */
    public static final String DEFAULT_SLIDER_IMAGE_RESOURCE_PATH = "META-INF/cut-image/resource";
    /**
     * 默认的template资源文件路径.
     */
    public static final String DEFAULT_SLIDER_IMAGE_TEMPLATE_PATH = "META-INF/cut-image/template";

    public StandardSliderImageCaptchaGenerator(ImageCaptchaResourceManager imageCaptchaResourceManager) {
        super(imageCaptchaResourceManager);
    }

    public StandardSliderImageCaptchaGenerator(ImageCaptchaResourceManager imageCaptchaResourceManager, ImageTransform imageTransform) {
        super(imageCaptchaResourceManager);
        setImageTransform(imageTransform);
    }

    @Override
    protected void doInit(boolean initDefaultResource) {
        if (initDefaultResource) {
            initDefaultResource();
        }
    }

    @SneakyThrows
    @Override
    public ImageCaptchaInfo doGenerateCaptchaImage(GenerateParam param) {
        Boolean obfuscate = param.getObfuscate();
        ResourceMap templateImages = requiredRandomGetTemplate(param.getType(), param.getTemplateImageTag());
        Collection<InputStream> inputStreams = new LinkedList<>();
        try {
            Resource resourceImage = requiredRandomGetResource(param.getType(), param.getBackgroundImageTag());
            InputStream resourceInputStream = imageCaptchaResourceManager.getResourceInputStream(resourceImage);
            inputStreams.add(resourceInputStream);
            BufferedImage cutBackground = CaptchaImageUtils.wrapFile2BufferedImage(resourceInputStream);
            // 拷贝一份图片
            BufferedImage targetBackground = CaptchaImageUtils.copyImage(cutBackground, cutBackground.getType());

            InputStream fixedTemplateInput = getTemplateFile(templateImages, SliderCaptchaConstant.TEMPLATE_FIXED_IMAGE_NAME);
            inputStreams.add(fixedTemplateInput);
            BufferedImage fixedTemplate = CaptchaImageUtils.wrapFile2BufferedImage(fixedTemplateInput);

            InputStream activeTemplateInput = getTemplateFile(templateImages, SliderCaptchaConstant.TEMPLATE_ACTIVE_IMAGE_NAME);
            inputStreams.add(activeTemplateInput);
            BufferedImage activeTemplate = CaptchaImageUtils.wrapFile2BufferedImage(activeTemplateInput);


            InputStream matrixTemplateInput = getTemplateFile(templateImages, SliderCaptchaConstant.TEMPLATE_MATRIX_IMAGE_NAME);
            inputStreams.add(matrixTemplateInput);
            BufferedImage matrixTemplate = CaptchaImageUtils.wrapFile2BufferedImage(matrixTemplateInput);

//        BufferedImage cutTemplate = warpFile2BufferedImage(getTemplateFile(templateImages, CUT_IMAGE_NAME));

            // 获取随机的 x 和 y 轴
            int randomX = ThreadLocalRandom.current().nextInt(fixedTemplate.getWidth() + 5, targetBackground.getWidth() - fixedTemplate.getWidth() - 10);
            int randomY = ThreadLocalRandom.current().nextInt(targetBackground.getHeight() - fixedTemplate.getHeight());

            CaptchaImageUtils.overlayImage(targetBackground, fixedTemplate, randomX, randomY);
            if (obfuscate) {
                // 加入混淆滑块
                int obfuscateX = randomObfuscateX(randomX, fixedTemplate.getWidth(), targetBackground.getWidth());
                CaptchaImageUtils.overlayImage(targetBackground, fixedTemplate, obfuscateX, randomY);
            }
            BufferedImage cutImage = CaptchaImageUtils.cutImage(cutBackground, fixedTemplate, randomX, randomY);
            CaptchaImageUtils.overlayImage(cutImage, activeTemplate, 0, 0);
            CaptchaImageUtils.overlayImage(matrixTemplate, cutImage, 0, randomY);
            return wrapSliderCaptchaInfo(randomX, randomY, targetBackground, matrixTemplate, param, templateImages, resourceImage);
        } finally {
            // 使用完后关闭流
            for (InputStream inputStream : inputStreams) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * 包装成 SliderCaptchaInfo
     *
     * @param randomX         随机生成的 x轴
     * @param randomY         随机生成的 y轴
     * @param backgroundImage 背景图片
     * @param sliderImage     滑块图片
     * @param param           接口传入参数
     * @return SliderCaptchaInfo
     */
    @SneakyThrows
    public SliderImageCaptchaInfo wrapSliderCaptchaInfo(int randomX,
                                                        int randomY,
                                                        BufferedImage backgroundImage,
                                                        BufferedImage sliderImage,
                                                        GenerateParam param,
                                                        ResourceMap templateResource,
                                                        Resource resourceImage) {
        ImageTransformData transform = getImageTransform().transform(param, backgroundImage, sliderImage, resourceImage, templateResource);

        return SliderImageCaptchaInfo.of(randomX, randomY,
                transform.getBackgroundImageUrl(),
                transform.getTemplateImageUrl(),
                resourceImage.getTag(),
                templateResource.getTag(),
                backgroundImage.getWidth(), backgroundImage.getHeight(),
                sliderImage.getWidth(), sliderImage.getHeight()
        );
    }

    protected int randomObfuscateX(int sliderX, int slWidth, int bgWidth) {
        if (bgWidth / 2 > (sliderX + (slWidth / 2))) {
            // 右边混淆
            return ThreadLocalRandom.current().nextInt(sliderX + slWidth, bgWidth - slWidth);
        }
        // 左边混淆
        return ThreadLocalRandom.current().nextInt(slWidth, sliderX - slWidth);
    }

    /**
     * 初始化默认资源
     */
    public void initDefaultResource() {
        ResourceStore resourceStore = imageCaptchaResourceManager.getResourceStore();
        // 添加一些系统的资源文件
        resourceStore.addResource(CaptchaTypeConstant.SLIDER, new Resource(ClassPathResourceProvider.NAME, DEFAULT_SLIDER_IMAGE_RESOURCE_PATH.concat("/1.jpg")));

        // 添加一些系统的 模板文件
        ResourceMap template1 = new ResourceMap(DEFAULT_TAG, 4);
        template1.put(SliderCaptchaConstant.TEMPLATE_ACTIVE_IMAGE_NAME, new Resource(ClassPathResourceProvider.NAME, DEFAULT_SLIDER_IMAGE_TEMPLATE_PATH.concat("/1/active.png")));
        template1.put(SliderCaptchaConstant.TEMPLATE_FIXED_IMAGE_NAME, new Resource(ClassPathResourceProvider.NAME, DEFAULT_SLIDER_IMAGE_TEMPLATE_PATH.concat("/1/fixed.png")));
        template1.put(SliderCaptchaConstant.TEMPLATE_MATRIX_IMAGE_NAME, new Resource(ClassPathResourceProvider.NAME, DEFAULT_SLIDER_IMAGE_TEMPLATE_PATH.concat("/1/matrix.png")));
        resourceStore.addTemplate(CaptchaTypeConstant.SLIDER, template1);


        ResourceMap template2 = new ResourceMap(DEFAULT_TAG, 4);
        template2.put(SliderCaptchaConstant.TEMPLATE_ACTIVE_IMAGE_NAME, new Resource(ClassPathResourceProvider.NAME, DEFAULT_SLIDER_IMAGE_TEMPLATE_PATH.concat("/2/active.png")));
        template2.put(SliderCaptchaConstant.TEMPLATE_FIXED_IMAGE_NAME, new Resource(ClassPathResourceProvider.NAME, DEFAULT_SLIDER_IMAGE_TEMPLATE_PATH.concat("/2/fixed.png")));
        template2.put(SliderCaptchaConstant.TEMPLATE_MATRIX_IMAGE_NAME, new Resource(ClassPathResourceProvider.NAME, DEFAULT_SLIDER_IMAGE_TEMPLATE_PATH.concat("/2/matrix.png")));
        resourceStore.addTemplate(CaptchaTypeConstant.SLIDER, template2);
    }
}
