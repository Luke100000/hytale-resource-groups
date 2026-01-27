# Hytale Resource Groups

Allows mods to modify existing assets without needing to replace them entirely.

`Server/Item/ResourceGroups/MyGroup.json`

```json5
{
  // The resource type to add items to
  "ResourceType": {
    "Id": "Wood_Trunk"
  },
  // And a list of resources to add
  "Resources": [
    {
      // Either item ids
      "ItemId": "Rock_Aqua"
    },
    {
      // Or resource type ids
      "ResourceTypeId": "Sands"
    },
    {
      // Resource groups can reference other resource groups
      "ResourceTypeId": "AnotherGroup"
    }
  ]
}
```