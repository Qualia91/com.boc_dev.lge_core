package com.boc_dev.lge_core;

import com.boc_dev.lge_model.gcs.Component;
import com.boc_dev.lge_model.generated.components.ComponentType;
import com.boc_dev.lge_model.generated.components.GeometryObject;
import com.boc_dev.lge_model.generated.components.TransformObject;
import com.boc_dev.maths.objects.matrix.Matrix4f;

public class TreeUtils {

	// TODO definitely write tests for this, who knows if this works...
	// this function steps up the tree until it gets to the root node. from there it steps downwards to find the
	// highest transform that has a dirty flag set to true. If it finds one it will return it. If it doesnt (ie all
	// transforms has been resolved) it returns null.
	public TransformObject findRootDirtyTransform(Component component) {
		// if component has parent, get parent and run again
		if (component.getParent() != null) {
			TransformObject returnedComponent = findRootDirtyTransform(component.getParent());
			// if the returned component is not null, then we have found the highest transform with a dirty flag set true
			// so return it
			if (returnedComponent != null) {
				return returnedComponent;
			}
			// if it is null, we need to check this layers component for whether its a transform and dirty
			if (component.getComponentType().equals(ComponentType.TRANSFORM) && component.isDirty()) {
				// if it is a transform and dirty, return this to be entered into the transform resolver function
				return (TransformObject) component;
			}
			// if it is not, return null again so next layer can check
			return null;
		}
		// if it doesn't have parent, it is root node and we need to check for type and dirty flag
		else if (component.getComponentType().equals(ComponentType.TRANSFORM) && component.isDirty()) {
			// if it is a transform and dirty, return this to be entered into the transform resolver function
			return (TransformObject) component;
		}
		// if it doesn't and its not a transform and dirt, return null so the next level down can be checked
		else {
			return null;
		}
	}

	// this function takes in a component and a current world transform (as a matrix4f) and walks down the tree,
	// calculating the current transform if it comes across a transform object, or sending an update to the
	// rendering module if it comes across a renderable, with its id and a new matrix4f for it
	public void resolveTransformsAndSend(Component component, Matrix4f currentGlobalTransform, RenderingConversion renderingConversion) {

		// if its a transform object, update the currentGlobalTransform
		if (component.getComponentType().equals(ComponentType.TRANSFORM)) {
			TransformObject transformObject = (TransformObject) component;

			currentGlobalTransform = Matrix4f.Transform(
					transformObject.getPosition(),
					transformObject.getRotation().toMatrix(),
					transformObject.getScale()).multiply(currentGlobalTransform);
			// then set clean so the next pass doesn't bother with this transform
		}
		// if the component is a renderable, send an update to the graphics updating the instance transform of it
		else if (component.getComponentType().isRender()) {

			if (component.getComponentType().equals(ComponentType.GEOMETRY)) {
				GeometryObject geometryObject = (GeometryObject) component;
				currentGlobalTransform = geometryObject.getLocalTransformation().multiply(currentGlobalTransform);
			}

			renderingConversion.sendComponentInstanceUpdate(component, currentGlobalTransform);
		}

		// then set the components global transform matrix
		component.setGlobalTransform(currentGlobalTransform);
		component.setClean();

		// then get all children and run again
		for (Component child : component.getChildren()) {
			resolveTransformsAndSend(child, currentGlobalTransform, renderingConversion);
		}
	}

	public void resolveGlobalTransforms(Component component, Matrix4f globalTransform) {
		// first see if the current component is a transform
		if (component instanceof TransformObject) {
			// if it is, multiply the global transform by it to get the new most recent transform
			TransformObject transformObject = (TransformObject) component;
			globalTransform = globalTransform.multiply(
					Matrix4f.Transform(
							transformObject.getPosition(),
							transformObject.getRotation().toMatrix(),
							transformObject.getScale()));
		}

		// now set the dirty flag to clean and set the global transform
		component.setClean();
		component.setGlobalTransform(globalTransform);

		// now iterate over children, find dirty and do the same
		for (Component child : component.getChildren()) {
			if (child.isDirty()) {
				resolveGlobalTransforms(child, globalTransform);
			}
		}
	}

	public Component getClosestCleanComponent(Component component) {
		// check it has a parent
		if (component.getParent() != null) {
			// now check if its parent is dirty
			if (component.getParent().isDirty()) {
				// if it is, then find its parent transform
				return getClosestCleanComponent(component.getParent());
			}
			// if it isnt, return this
			return component.getParent();
		}
		// if it doesn't have a parent and it is dirty, return the current component
		return component;
	}

}
