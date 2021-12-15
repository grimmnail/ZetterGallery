package com.dantaeusb.zettergallery.network.packet;

import com.dantaeusb.zetter.storage.AbstractCanvasData;
import com.dantaeusb.zettergallery.ZetterGallery;
import com.dantaeusb.zettergallery.network.ClientHandler;
import com.dantaeusb.zettergallery.storage.OfferPaintingData;
import com.dantaeusb.zettergallery.trading.PaintingMerchantOffer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LogicalSidedProvider;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Vector;
import java.util.function.Supplier;

/**
 * @todo: Is that okay that we don't have classic handler here?
 */
public class SGallerySalesPacket {
    static final int MAX_NAME_LENGTH = 128;
    static final int MAX_AUTHOR_LENGTH = 64;

    private final boolean sellAllowed;
    private final List<PaintingMerchantOffer> offers;

    public SGallerySalesPacket(boolean sellAllowed, List<PaintingMerchantOffer> offers) {
        this.sellAllowed = sellAllowed;
        this.offers = offers;
    }

    public boolean isSellAllowed() {
        return this.sellAllowed;
    }

    public List<PaintingMerchantOffer> getOffers() {
        return this.offers;
    }

    /**
     * Reads the raw packet data from the data stream.
     */
    public static SGallerySalesPacket readPacketData(FriendlyByteBuf networkBuffer) {
        try {
            final boolean saleAllowed = networkBuffer.readBoolean();
            final int size = networkBuffer.readInt();
            int i = 0;

            Vector<PaintingMerchantOffer> offers = new Vector<>();

            while (i < size) {
                final UUID uuid = networkBuffer.readUUID();
                final String title = networkBuffer.readUtf(MAX_NAME_LENGTH);
                final String authorName = networkBuffer.readUtf(MAX_AUTHOR_LENGTH);
                final AbstractCanvasData.Resolution resolution = AbstractCanvasData.Resolution.get(networkBuffer.readInt());
                final int sizeH = networkBuffer.readInt();
                final int sizeW = networkBuffer.readInt();
                final byte[] color = networkBuffer.readByteArray();
                final int price = networkBuffer.readInt();

                OfferPaintingData paintingData = OfferPaintingData.create(uuid, authorName, title, resolution, sizeW * resolution.getNumeric(), sizeH * resolution.getNumeric(), color);

                offers.add(new PaintingMerchantOffer(paintingData, price));

                i++;
            }

            return new SGallerySalesPacket(saleAllowed, offers);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            ZetterGallery.LOG.warn("Exception while reading SGallerySalesPacket: " + e);
            return null;
        }
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    public void writePacketData(FriendlyByteBuf networkBuffer) {
        networkBuffer.writeBoolean(this.sellAllowed);
        networkBuffer.writeInt(this.offers.size());

        for (PaintingMerchantOffer merchantOffer : this.offers) {
            OfferPaintingData paintingData = merchantOffer.getPaintingData();

            int resolution = paintingData.getResolution().getNumeric();
            byte[] color = new byte[paintingData.getColorDataBuffer().remaining()];
            paintingData.getColorDataBuffer().get(color);

            networkBuffer.writeUUID(paintingData.getUniqueId());
            networkBuffer.writeUtf(paintingData.getPaintingName(), MAX_NAME_LENGTH);
            networkBuffer.writeUtf(paintingData.getAuthorName(), MAX_AUTHOR_LENGTH);
            networkBuffer.writeInt(paintingData.getResolution().getNumeric());
            networkBuffer.writeInt(paintingData.getHeight() / resolution);
            networkBuffer.writeInt(paintingData.getWidth() / resolution);
            networkBuffer.writeByteArray(color);
            networkBuffer.writeInt(merchantOffer.getPrice());
        }
    }

    public static void handle(final SGallerySalesPacket packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        Optional<Level> clientWorld = LogicalSidedProvider.CLIENTWORLD.get(sideReceived);
        if (!clientWorld.isPresent()) {
            ZetterGallery.LOG.warn("SGalleryOffersPacket context could not provide a ClientWorld.");
            return;
        }

        ctx.enqueueWork(() -> ClientHandler.processPaintingMerchantOffers(packetIn, clientWorld.get()));
    }

    @Override
    public String toString()
    {
        return "SGalleryOffersPacket[]";
    }
}