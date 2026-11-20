package net.bible.android.view.activity.nav.ports

import net.bible.service.common.CommonUtils
import net.bible.sharedcore.links.TrustedLinkDomains
import net.bible.sharedui.app.ports.ExternalLinks

class AndroidExternalLinks : ExternalLinks {
    override val askBeforeOpening: Boolean get() = CommonUtils.isDiscrete
    override val trustedDomains: TrustedLinkDomains get() = TrustedLinkDomains(CommonUtils.settings)
    override fun open(url: String) = CommonUtils.openLinkNow(url)
}
