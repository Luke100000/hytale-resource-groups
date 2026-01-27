package net.conczin.resourcegroups;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.flogger.LazyArgs.lazy;


public class ResourceGroupsPlugin extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.get("ResourceGroups");
    public static final ItemResourceType[] EMPTY_ITEM_RESOURCE_TYPES = new ItemResourceType[0];

    private static boolean currentlyPatching = false;

    // Use custom registry to have access to loaded resource groups immediately
    private static final Map<String, ResourceGroup> loadedResourceGroups = new Object2ObjectOpenHashMap<>();

    // Keep track of original resource types for items
    private static final Map<String, ItemResourceType[]> originalResourceTypes = new Object2ObjectOpenHashMap<>();

    // The final lookups of resource types to be added
    private static final Map<String, Set<ItemResourceType>> resourceTypeLookup = new Object2ObjectOpenHashMap<>();
    private static final Map<String, Set<ItemResourceType>> itemIdLookup = new Object2ObjectOpenHashMap<>();

    public ResourceGroupsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        AssetRegistry.register(HytaleAssetStore.builder(ResourceGroup.class, new DefaultAssetMap<>())
                .setPath("Item/ResourceGroups")
                .setCodec(ResourceGroup.CODEC)
                .setKeyFunction(ResourceGroup::getId)
                .loadsAfter(ResourceType.class, Item.class)
                .build()
        );

        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, ResourceGroupsPlugin::onItemAssetLoad);

        this.getEventRegistry().register(LoadedAssetsEvent.class, ResourceGroup.class, ResourceGroupsPlugin::onResourceGroupAssetLoad);
        this.getEventRegistry().register(RemovedAssetsEvent.class, ResourceGroup.class, ResourceGroupsPlugin::onResourceGroupAssetRemove);
    }

    private static void onItemAssetLoad(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        if (currentlyPatching) return;

        // Patch new items immediately
        List<Item> patchedItems = new LinkedList<>();
        for (Item item : event.getLoadedAssets().values()) {
            patchItem(item, patchedItems, true);
        }
        patchItems(patchedItems);
    }

    private static void onResourceGroupAssetLoad(LoadedAssetsEvent<String, ResourceGroup, DefaultAssetMap<String, ResourceGroup>> event) {
        for (ResourceGroup group : event.getLoadedAssets().values()) {
            loadedResourceGroups.put(group.getId(), group);
        }
        rebuildLookup();
    }

    private static void onResourceGroupAssetRemove(@Nonnull RemovedAssetsEvent<String, ResourceGroup, DefaultAssetMap<String, ResourceGroup>> event) {
        for (String id : event.getRemovedAssets()) {
            loadedResourceGroups.remove(id);
        }
        rebuildLookup();
    }

    private static void rebuildLookup() {
        long time = System.nanoTime();

        // Resolve all groups
        Map<ItemResourceType, ResolvedGroups> resolvedGroups = new Object2ObjectOpenHashMap<>();
        for (ResourceGroup group : loadedResourceGroups.values()) {
            resolvedGroups.computeIfAbsent(group.getItemResourceType(), _ -> new ResolvedGroups()).addGroup(group);
        }

        // Create a lookup for both resource type and item type to resource ids which should be added
        resourceTypeLookup.clear();
        itemIdLookup.clear();
        for (Map.Entry<ItemResourceType, ResolvedGroups> entry : resolvedGroups.entrySet()) {
            for (String resourceTypeId : entry.getValue().resourceTypeIds) {
                resourceTypeLookup.computeIfAbsent(resourceTypeId, _ -> new HashSet<>()).add(entry.getKey());
            }
            for (String itemId : entry.getValue().itemIds) {
                itemIdLookup.computeIfAbsent(itemId, _ -> new HashSet<>()).add(entry.getKey());
            }
        }

        // Patch all items
        List<Item> patchedItems = new LinkedList<>();
        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            patchItem(item, patchedItems, false);
        }

        LOGGER.atInfo().log("Patching took %.2f ms", (System.nanoTime() - time) / 1_000_000.0);

        patchItems(patchedItems);
    }

    @SuppressWarnings("CommentedOutCode")
    private static void patchItems(List<Item> patchedItems) {
        // TODO: Reload affected recipes somehow
        /*
        if (!patchedItems.isEmpty()) {
            currentlyPatching = true;
            Item.getAssetStore().loadAssets("Conczin:ResourceGroupsPatch", patchedItems);
            currentlyPatching = false;
        }

        // Also reload recipes
        List<CraftingRecipe> reloadRecipes = new ObjectArrayList<>();
        for (Item item : patchedItems) {
            if (item.hasRecipesToGenerate()) {
                item.collectRecipesToGenerate(reloadRecipes);
            }
        }
        if (!reloadRecipes.isEmpty()) {
            CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", reloadRecipes);
        }
         */
    }

    private static void patchItem(Item item, List<Item> patchedItems, boolean resetOriginal) {
        ItemResourceType[] currentResourceTypes = item.getResourceTypes();
        if (currentResourceTypes == null) {
            currentResourceTypes = EMPTY_ITEM_RESOURCE_TYPES;
        }

        // Look up and patch
        if (resetOriginal || !originalResourceTypes.containsKey(item.getId())) {
            originalResourceTypes.put(item.getId(), currentResourceTypes);
        }
        ItemResourceType[] resourceTypes = originalResourceTypes.get(item.getId());

        // Combine all resource types
        Set<ItemResourceType> newResourceTypes = new HashSet<>(Arrays.asList(resourceTypes));
        if (itemIdLookup.containsKey(item.getId())) {
            newResourceTypes.addAll(itemIdLookup.get(item.getId()));
        }
        for (ItemResourceType itemResourceType : resourceTypes) {
            addResourceTypesRecursive(itemResourceType, newResourceTypes);
        }

        // Log if patched
        if (currentResourceTypes.length != newResourceTypes.size()) {
            LOGGER.atFine().log(
                    "Patching item %s with additional resource types: %s",
                    item.getId(),
                    lazy(() -> newResourceTypes.stream()
                            .map(v -> v.id)
                            .collect(Collectors.joining(", ")))
            );

            // Apply
            overwriteResourceTypes(item, newResourceTypes);
            patchedItems.add(item);
        }
    }

    private static void addResourceTypesRecursive(ItemResourceType itemResourceType, Set<ItemResourceType> finalResourceTypes) {
        if (resourceTypeLookup.containsKey(itemResourceType.id)) {
            for (ItemResourceType resourceType : resourceTypeLookup.get(itemResourceType.id)) {
                finalResourceTypes.add(resourceType);
                addResourceTypesRecursive(resourceType, finalResourceTypes);
            }
        }
    }

    static void overwriteResourceTypes(Item item, Set<ItemResourceType> list) {
        try {
            Field f = Item.class.getDeclaredField("resourceTypes");
            f.setAccessible(true);
            if (list.isEmpty()) {
                f.set(item, null);
            } else {
                f.set(item, list.toArray(new ItemResourceType[0]));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static class ResolvedGroups {
        Set<String> itemIds = new HashSet<>();
        Set<String> resourceTypeIds = new HashSet<>();

        void addGroup(ResourceGroup group) {
            for (ResourceIdentifier resource : group.getResources()) {
                if (resource.getItemId() != null) {
                    itemIds.add(resource.getItemId());
                } else if (resource.getResourceTypeId() != null) {
                    resourceTypeIds.add(resource.getResourceTypeId());
                } else {
                    LOGGER.atWarning().log("Resource in group " + group.getId() + " has neither itemId nor resourceTypeId set.");
                }
            }
        }
    }
}