package com.ascensionlib.client;

import com.ascensionlib.fusion.FusionPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The craft screen's Fuse button. The fusion screen itself is a web page (WebGUI) the server opens, so all the client does is ask the
 * server to open it for this host.
 */
final class FusionClient {
    private FusionClient() {}

    static void open(String hostId) {
        if (ClientPlayNetworking.canSend(FusionPayloads.Open.TYPE)) ClientPlayNetworking.send(new FusionPayloads.Open(hostId));
    }
}
