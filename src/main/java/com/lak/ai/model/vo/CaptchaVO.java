package com.lak.ai.model.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaptchaVO {

    private String captchaKey;

    /** 生产环境应替换为 base64 PNG 图片，开发环境暂用明文 */
    @Deprecated
    private String captchaText;
}
