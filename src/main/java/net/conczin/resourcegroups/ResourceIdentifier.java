package net.conczin.resourcegroups;

import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;

import javax.annotation.Nullable;

public class ResourceIdentifier {
    public static final Codec<ResourceIdentifier> CODEC = AssetBuilderCodec.builder(
                    ResourceIdentifier.class,
                    ResourceIdentifier::new
            )
            .append(
                    new KeyedCodec<>("ItemId", Codec.STRING),
                    (item, v) -> item.itemId = v,
                    item -> item.itemId
            )
            .addValidator(Item.VALIDATOR_CACHE.getValidator())
            .add()
            .append(
                    new KeyedCodec<>("ResourceTypeId", Codec.STRING),
                    (item, v) -> item.resourceTypeId = v,
                    item -> item.resourceTypeId
            )
            .addValidator(ResourceType.VALIDATOR_CACHE.getValidator())
            .add()
            .build();

    @Nullable
    protected String itemId;

    @Nullable
    protected String resourceTypeId;

    @Nullable
    public String getItemId() {
        return itemId;
    }

    @Nullable
    public String getResourceTypeId() {
        return resourceTypeId;
    }
}
