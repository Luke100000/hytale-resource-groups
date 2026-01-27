package net.conczin.resourcegroups;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;

import javax.annotation.Nullable;

public class ResourceGroup implements JsonAssetWithMap<String, DefaultAssetMap<String, ResourceGroup>> {
    public static final BuilderCodec<ItemResourceType> ITEM_RESOURCE_TYPE_CODEC = BuilderCodec.builder(ItemResourceType.class, ItemResourceType::new)
            .append(new KeyedCodec<>("Id", Codec.STRING), (itemResourceType, s) -> itemResourceType.id = s, itemResourceType -> itemResourceType.id)
            .addValidator(ResourceType.VALIDATOR_CACHE.getValidator())
            .addValidator(Validators.nonNull())
            .add()
            .append(
                    new KeyedCodec<>("Quantity", Codec.INTEGER),
                    (itemResourceType, s) -> itemResourceType.quantity = s,
                    itemResourceType -> itemResourceType.quantity
            )
            .addValidator(Validators.greaterThan(0))
            .add()
            .build();

    public static final AssetBuilderCodec<String, ResourceGroup> CODEC = AssetBuilderCodec.builder(
                    ResourceGroup.class,
                    ResourceGroup::new,
                    Codec.STRING,
                    (t, id) -> t.id = id,
                    t -> t.id,
                    (t, data) -> t.data = data,
                    t -> t.data
            )
            .append(
                    new KeyedCodec<>("ResourceType", ITEM_RESOURCE_TYPE_CODEC, true),
                    (t, v) -> t.itemResourceType = v,
                    t -> t.itemResourceType
            )
            .add()
            .append(
                    new KeyedCodec<>("Resources", new ArrayCodec<>(ResourceIdentifier.CODEC, ResourceIdentifier[]::new)),
                    (t, v) -> t.resources = v,
                    t -> t.resources
            )
            .add()
            .build();

    private AssetExtraInfo.Data data;
    protected String id;

    protected ItemResourceType itemResourceType;
    protected ResourceIdentifier[] resources;


    @Nullable
    public String getId() {
        return id;
    }

    public ItemResourceType getItemResourceType() {
        return itemResourceType;
    }

    public ResourceIdentifier[] getResources() {
        return resources;
    }
}
