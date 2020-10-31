package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.gcs_model.gcs.Component;
import com.nick.wood.game_engine.gcs_model.generated.components.*;
import com.nick.wood.graphics_library.materials.BasicMaterial;
import com.nick.wood.graphics_library.materials.Material;
import com.nick.wood.graphics_library.materials.NormalMaterial;

public class MaterialBuilder {
	public Material build(MaterialObject materialObject) {

		// get children of material
		// there are a couple possible material types it can be (2 at the moment, could be more in the future.
		// if it only has a texture map, it is basic
		// if it has normal and texture, it is normal
		// will use the last 2 of each if multiple of each are found. oh well...
		String texturePath = "";
		String normalPath = "";
		for (Component child : materialObject.getChildren()) {
			if (child.getComponentType().equals(ComponentType.TEXTURE)) {
				texturePath = ((TextureObject) child).getPath();
			} else if (child.getComponentType().equals(ComponentType.NORMALMAP)) {
				normalPath = ((NormalMapObject) child).getPath();
			}
		}

		Material material;

		if (normalPath.isBlank()) {
			material = new BasicMaterial(
					materialObject.getUuid(),
					texturePath
			);
		} else if (!texturePath.isBlank()) {
			material = new NormalMaterial(
					materialObject.getUuid(),
					texturePath,
					normalPath,
					materialObject.getDiffuseColour(),
					materialObject.getSpecularColour(),
					materialObject.getShininess(),
					materialObject.getReflectance()
			);
		}
		// if no textures in material, return basic material with default texture in
		else {
			material = new BasicMaterial(
					materialObject.getUuid(),
					"DEFAULT"
			);
		}

		return material;
	}
}
