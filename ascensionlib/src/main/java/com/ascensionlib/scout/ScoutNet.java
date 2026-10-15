package com.ascensionlib.scout;

/** Registers the screens' server halves. Every screen is a web page now (see {@code WebUi}); there are no client packets left. */
public final class ScoutNet {
    private ScoutNet() {}

    public static void register() {
        com.ascensionlib.web.WebUi.init();
        InspectWeb.register();
        com.ascensionlib.web.HudWeb.register();
        com.ascensionlib.wallet.WalletWeb.register();
        com.ascensionlib.craft.CraftNet.register();
        com.ascensionlib.fusion.FusionNet.register();
    }
}
