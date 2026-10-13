package local.doomed.russianfresh;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid="doomed_ru_fresh",value=Dist.CLIENT)
public final class SystemMessages {
    @SubscribeEvent public static void receive(ClientChatReceivedEvent event) {
        if(event instanceof ClientChatReceivedEvent.Player)return;
        // Only sufficiently specific original Doomed system sentences are recognized.
        event.setMessage(RussianText.serverComponent(event.getMessage()));
    }
}
