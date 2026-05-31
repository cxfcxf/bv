package dev.aaa1115910.bv.entity

import android.content.Context

enum class CdnType(val urlKeyword: String?) {
    Auto(null),
    Akamai("mirrorakam"),   // upos-hz-mirrorakam.akamaized.net (overseas, best for US)
    Ali("mirrorali"),        // upos-sz-mirrorali.bilivideo.com (Alibaba Cloud)
    HW("mirrorhw"),          // upos-sz-mirrorhw.bilivideo.com (Huawei Cloud)
    COS("mirrorcos"),        // upos-sz-mirrorcos.bilivideo.com (Tencent COS)
    KS3("mirrorks3"),        // upos-sz-mirrorks3.bilivideo.com (Kingsoft)
    Kodo("mirrorkodo");      // upos-sz-mirrorkodo.bilivideo.com (Qiniu)

    fun getDisplayName(context: Context) = when (this) {
        Auto -> "自动"
        Akamai -> "Akamai (海外)"
        Ali -> "阿里云"
        HW -> "华为云"
        COS -> "腾讯云"
        KS3 -> "金山云"
        Kodo -> "七牛云"
    }
}
