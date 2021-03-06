
package com.guida.areraser

import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import org.opencv.core.Point
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min


//A class for defining the bounding box that the user places
class PlaneManager : ViewSizer{
    val LEFTMOSTNODE = "leftmostnode"
    val RIGHTMOSTNODE = "rightmostnode"
    val TOPMOSTNODE = "topmostnode"
    val BOTTOMMOSTNODE = "bottommostnode"

    private var globalBoundingPoints: MutableList<Vector3> = mutableListOf()
    private var globalBoundingNodes: MutableList<Node?> = mutableListOf(Node(), Node(), Node(), Node())
    private var boundNames = listOf<String>(LEFTMOSTNODE, RIGHTMOSTNODE, TOPMOSTNODE, BOTTOMMOSTNODE)

    private var TAG = "PLANEMANAGER"

    //Box dimensions
    val height = 5.0f
    val width = 5.0f
    val depth = 5.0f

    //Bounding points for plane
    var minX = 1000.0f
    var minZ = 1000.0f
    var maxX = -1000.0f
    var maxZ = -1000.0f

    //Box color
    val color = Color(255.0f, 0.0f, 0.0f, 0.7f)

    //Distance from the user the box is placed
    var rayDistance = 30.0f

    //Node to place the box on
    var transformableNode: TransformableNode? = null
    var transformableCast: TransformableNode? = null

    fun placeBoxRayCast(
        context: Context,
        xpos: Float, ypos: Float,
        arFragment: ArFragment,
        distance: Float):
            TransformableNode? {
        val touchRay = arFragment.arSceneView.scene.camera.screenPointToRay(xpos, ypos)
        MaterialFactory.makeTransparentWithColor(context, color)
        .thenAccept{ material ->
            val cubeVector = Vector3(width, height, depth)
            val positionVector = touchRay.getPoint(distance)
            val modelRenderable = ShapeFactory.makeCube(
                    cubeVector,
                    positionVector,
                    material)
            modelRenderable.setShadowCaster(true)
            modelRenderable.setShadowReceiver(false)

            transformableCast?.setParent(null)
            transformableCast = TransformableNode(arFragment.getTransformationSystem())
            transformableCast?.setParent(arFragment.getArSceneView().getScene())
            transformableCast?.setRenderable(modelRenderable)
            arFragment.arSceneView.scene.addChild(transformableCast)
            transformableCast?.select()
        }
        return transformableCast
    }

    fun placeBoxOnPlane(
            arFragment: ArFragment,
            hitResult: HitResult
            ): TransformableNode? {
        var anchor = hitResult.trackable.createAnchor(
                hitResult.hitPose.compose(Pose.makeTranslation(0.3f,0.0f,0.3f))
        )
        Texture.builder()
            .setSource(arFragment.context, R.drawable.bounding_box)
            .build().thenAccept { texture: Texture ->
                MaterialFactory.makeOpaqueWithTexture(
                    arFragment.context,
                    texture
                ).thenAccept { material ->
                    val modelRenderable = ShapeFactory.makeCube(
                        Vector3(0.3f, 0.001f, 0.3f),
                        Vector3(0.0f, 0.0f, 0.0f),
                        material
                    )
                    material.setFloat(MaterialFactory.MATERIAL_METALLIC, 1.0f)
                    material.setFloat(MaterialFactory.MATERIAL_REFLECTANCE, 0.5f)
                    material.setFloat(MaterialFactory.MATERIAL_ROUGHNESS, 0.4f)
                    modelRenderable.isShadowCaster = false
                    modelRenderable.isShadowReceiver = true
                    val anchorNode = AnchorNode(anchor)
                    anchorNode.setParent(arFragment.arSceneView.scene)
                    transformableNode?.setParent(null)
                    transformableNode = TransformableNode(arFragment.transformationSystem)
                    transformableNode?.rotationController?.isEnabled = false
                    transformableNode?.scaleController?.isEnabled = false
                    transformableNode?.renderable = modelRenderable
                    transformableNode?.setParent(anchorNode)
                    //transformableNode?.worldRotation = arFragment.arSceneView.scene.camera.worldRotation
                    //transformableNode?.localRotation =
                      //  Quaternion.axisAngle(Vector3(1f, 0f, 0f), 90f)

                }.exceptionally {
                    val builder = AlertDialog.Builder(arFragment.context)
                    builder.setMessage(it.message).setTitle("${TAG}: Error")
                    builder.create().show()
                    return@exceptionally null
                }
            }.exceptionally {
                val builder = AlertDialog.Builder(arFragment.context)
                builder.setMessage(it.message).setTitle("${TAG}: Error")
                builder.create().show()
                return@exceptionally null
            }
        return transformableNode
    }

    fun updateBoundingBoxTexture(
        context: Context,
        textureID: Int,
        isShadowReceiver: Boolean,
        metallic: Float,
        roughness: Float,
        reflectance: Float) {
        Texture.builder()
            .setSource(context, textureID)
            .build().thenAccept { texture: Texture ->
                MaterialFactory.makeOpaqueWithTexture(
                    context,
                    texture
                ).thenAccept { material ->
                    val modelRenderable = ShapeFactory.makeCube(
                        Vector3(0.3f, 0.001f, 0.3f),
                        Vector3(0.0f, 0.0f, 0.0f),
                        material
                    )

                    material.setFloat(MaterialFactory.MATERIAL_METALLIC, 0.0f)
                    material.setFloat(MaterialFactory.MATERIAL_REFLECTANCE, 0.5f)
                    material.setFloat(MaterialFactory.MATERIAL_ROUGHNESS, 0.4f)

                    modelRenderable.isShadowCaster = false
                    modelRenderable.isShadowReceiver = isShadowReceiver
                    transformableNode?.renderable = modelRenderable
                    //transformableNode?.worldRotation = arFragment.arSceneView.scene.camera.worldRotation
                    //transformableNode?.localRotation =
                    //  Quaternion.axisAngle(Vector3(1f, 0f, 0f), 90f)

                }.exceptionally {
                    val builder = AlertDialog.Builder(context)
                    builder.setMessage(it.message).setTitle("${TAG}: Error")
                    builder.create().show()
                    return@exceptionally null
                }
            }.exceptionally {
                val builder = AlertDialog.Builder(context)
                builder.setMessage(it.message).setTitle("${TAG}: Error")
                builder.create().show()
                return@exceptionally null
            }
    }

    fun tracePlane(plane:Plane, arFragment: ArFragment){
        var topPlane = getTopMostPlane(plane)
        var polygonPoints = topPlane.polygon
        for (i in 0 until polygonPoints.remaining() step 2){
            var floatPoint = plane.centerPose.transformPoint(
                floatArrayOf(
                    polygonPoints[i], 0.0f, polygonPoints[i+1]
                )
            )
            var globalPoint = Vector3(
                floatPoint[0],
                floatPoint[1],
                floatPoint[2]
            )
            placeBoundingPoint(globalPoint, arFragment, plane, "Edge point")
        }
    }

    //returns a sub array of the plane polygon that has only the farthest most points in all directions
    fun getLocalMaxMinPoints(plane: Plane): FloatArray {
        var topPlane = getTopMostPlane(plane)
        Log.d(TAG, "Getting local LTRB points...")
        var boundingPoints = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        var polygonPoints = topPlane.polygon
        for (i in 0 until polygonPoints.remaining() step 2){
            Log.d(TAG, "ITERATING THROUGH POINTS: POINT: ${polygonPoints.get(i)}")

            var x = polygonPoints.get(i)
            var z = polygonPoints.get(i+1)
            if (x < minX){
                minX = x
                boundingPoints[0] = x
                boundingPoints[1] = z
            }
            if (x > maxX){
                maxX = x
                boundingPoints[2] = x
                boundingPoints[3] = z
            }
            if (z < minZ){
                minZ = z
                boundingPoints[4] = x
                boundingPoints[5] = z
            }
            if (z > maxZ){
                maxZ
                boundingPoints[6] = x
                boundingPoints[7] = z
            }
        }
        Log.d(TAG, "ITERATED THROUGH ALL POINTS: BOUNDING POINTS: (" +
                "${boundingPoints[0]}, ${boundingPoints[1]}) " +
                "${boundingPoints[2]}, ${boundingPoints[3]}) " +
                "${boundingPoints[4]}, ${boundingPoints[5]}) " +
                "${boundingPoints[6]}, ${boundingPoints[7]})")
        minX = 1000.0f
        minZ = 1000.0f
        maxX = -1000.0f
        maxZ = -1000.0f
        return boundingPoints
    }

    //computes GlobalBoundingPoint for all planes in the AR session
    fun getCombinedGlobalBoundingPoints(arFragment: ArFragment): MutableList<Vector3> {
        var planes = getPlanes(arFragment)
        var combinedGlobalBoundingPoints = mutableListOf<Vector3>()

        //get the max LRTB points from each plane
        for(plane in planes!!.iterator()){
            //get the planes global bounding points
            var globalBoundingPoints: MutableList<Vector3> = getGlobalBoundingPoints(getLocalMaxMinPoints(plane), plane.centerPose)
            //check if we are the first plane
            if(combinedGlobalBoundingPoints.isEmpty()){
                combinedGlobalBoundingPoints = globalBoundingPoints
            } else {
                //get Left point
                if(globalBoundingPoints[0].x < combinedGlobalBoundingPoints[0].x){
                    combinedGlobalBoundingPoints[0] = globalBoundingPoints[0]
                }
                //get Right point
                if(globalBoundingPoints[1].x > combinedGlobalBoundingPoints[1].x){
                    combinedGlobalBoundingPoints[1] = globalBoundingPoints[1]
                }
                //get Top point
                if(globalBoundingPoints[2].z < combinedGlobalBoundingPoints[2].z){
                    combinedGlobalBoundingPoints[2] = globalBoundingPoints[2]
                }
                //get Bottom point
                if(globalBoundingPoints[3].z > combinedGlobalBoundingPoints[3].z){
                    combinedGlobalBoundingPoints[3] = globalBoundingPoints[3]
                }
            }
        }
        return combinedGlobalBoundingPoints
    }
/*
    fun getRectangleBounds(globalBoundingPoints: MutableList<Vector3>): MutableList<Vector3>{
        Log.d(TAG, "Global bounds: ${
            globalBoundingPoints[0].x}, " +
                "${globalBoundingPoints[0].z}, " +
                "${globalBoundingPoints[1].x}, " +
                "${globalBoundingPoints[1].z}, " +
                "${globalBoundingPoints[2].x}, " +
                "${globalBoundingPoints[2].z}, " +
                "${globalBoundingPoints[3].x}, " +
                "${globalBoundingPoints[3].z}, ")

        return mutableListOf(
            min(min(globalBoundingPoints[0].x, globalBoundingPoints[1].x),min(globalBoundingPoints[2].x, globalBoundingPoints[3].x)),
            max(max(globalBoundingPoints[0].x, globalBoundingPoints[1].x),max(globalBoundingPoints[2].x, globalBoundingPoints[3].x)),
            min(min(globalBoundingPoints[0].z, globalBoundingPoints[1].z),min(globalBoundingPoints[2].z, globalBoundingPoints[3].z)),
            max(max(globalBoundingPoints[0].z, globalBoundingPoints[1].z),max(globalBoundingPoints[2].z, globalBoundingPoints[3].z))
            )
    }

 */

    //returns global x,y,z coordinates of bounding points
    private fun getGlobalBoundingPoints(lrtb: FloatArray, centerPose: Pose): MutableList<Vector3> {
        Log.d(TAG, "GETTING GLOBAL POINTS...")

        var globalBounds: MutableList<Vector3> = mutableListOf()
        var globalPointL = centerPose.transformPoint(
            floatArrayOf(
                lrtb[0], 0.0f, lrtb[1]
            )
        )
        globalBounds?.add(Vector3(globalPointL[0], globalPointL[1], globalPointL[2]))
        var globalPointT = centerPose.transformPoint(
            floatArrayOf(
                lrtb[2], 0.0f, lrtb[3]
            )
        )
        globalBounds?.add(Vector3(globalPointT[0], globalPointT[1], globalPointT[2]))
        var globalPointR = centerPose.transformPoint(
            floatArrayOf(
                lrtb[4], 0.0f, lrtb[5]
            )
        )
        globalBounds?.add(Vector3(globalPointR[0], globalPointR[1], globalPointR[2]))
        var globalPointB = centerPose.transformPoint(
            floatArrayOf(
                lrtb[6], 0.0f, lrtb[7]
            )
        )
        globalBounds?.add(Vector3(globalPointB[0], globalPointB[1], globalPointB[2]))
        Log.d(TAG, "GOT GLOBAL BOUNDS: $globalBounds")
        return globalBounds
    }

    fun placeAllBoundingNodes(arFragment: ArFragment){
        globalBoundingPoints = getCombinedGlobalBoundingPoints(arFragment)
        for(i in 0..3){
            if(globalBoundingNodes[i]?.parent != null){
                Log.d(TAG, "node is not null ${globalBoundingNodes[i]?.toString()} parent: ${globalBoundingNodes[i]?.parent.toString()}")
                globalBoundingNodes[i]?.parent?.setParent(null)
                globalBoundingNodes[i]?.parent?.removeChild(globalBoundingNodes[i])
                globalBoundingNodes[i]?.setParent(null)
                Log.d(TAG, "node is now null ${globalBoundingNodes[i]?.toString()} parent: ${globalBoundingNodes[i]?.parent.toString()}")
            }
            globalBoundingNodes[i] = placeBoundingPoint(globalBoundingPoints[i], arFragment!!, getTopMostPlane(getPlanes(arFragment)!!.first()), boundNames[i])
        }
    }

    //places a plane edge indicator at 'point'
    fun placeBoundingPoint(point: Vector3, arFragment: ArFragment, plane: Plane, name: String): Node{
        Log.d(TAG, "PLACING BOUNDING POINTS: PLACING: $point")
        var boundingNode = Node()
        var topPlane = getTopMostPlane(plane)
        //get anchor for plane
        var pointPose = Pose(floatArrayOf(point.x, point.y, point.z), floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f))
        Log.d(TAG, "New Pose Translation: ${pointPose.translation[0]}, ${pointPose.translation[1]}, ${pointPose.translation[2]}")

        var planeCenterAnchor = topPlane?.createAnchor(pointPose)
        val anchorNode = AnchorNode(planeCenterAnchor)
        anchorNode.setParent(arFragment.arSceneView.scene)
        var color = Color(255.0f, 255.0f, 255.0f)
        if(name == LEFTMOSTNODE) color = Color(255.0f, 0.0f, 0.0f)
        if(name == RIGHTMOSTNODE) color = Color(0.0f, 255.0f, 0.0f)
        if(name == TOPMOSTNODE) color = Color(0.0f, 0.0f, 255.0f)
        if(name == BOTTOMMOSTNODE) color = Color(255.0f, 255.0f, 0.0f)

        Texture.builder()
            .setSource(arFragment.context, R.drawable.sample_texture)
            .build().thenAccept { texture: Texture ->
                MaterialFactory.makeOpaqueWithColor(
                    arFragment.context,
                    color
                ).thenAccept{material ->
                    val modelRenderable = ShapeFactory.makeCylinder(
                        0.03f,
                        0.1f,
                        Vector3(0.0f, 0.0f, 0.0f),
                        material
                    )
                    modelRenderable.isShadowCaster = false
                    modelRenderable.isShadowReceiver = false
                    boundingNode?.setParent(anchorNode)
                    boundingNode?.renderable = modelRenderable
                    boundingNode!!.setName(name)
                }.exceptionally {
                    val builder = AlertDialog.Builder(arFragment.context)
                    builder.setMessage(it.message).setTitle("${TAG}: Error")
                    builder.create().show()
                    return@exceptionally null
                }
            }.exceptionally {
                val builder = AlertDialog.Builder(arFragment.context)
                builder.setMessage(it.message).setTitle("${TAG}: Error")
                builder.create().show()
                return@exceptionally null
            }
        return boundingNode
    }

    //get the topmost plane
    fun getTopMostPlane(plane: Plane): Plane{
        if(plane.subsumedBy != null){
            Log.d(TAG, "plane has been subsumed")
            return plane.subsumedBy
        } else {
            Log.d(TAG, "plane has not been subsumed")
            return plane
        }
    }

    fun getPlaneCount(arFragment: ArFragment): Int{
        return arFragment.arSceneView.session?.getAllTrackables(Plane::class.java)?.size!!.toInt()
    }

    fun getPlanes(arFragment: ArFragment): MutableCollection<Plane>?{
        return arFragment.arSceneView.session?.getAllTrackables(Plane::class.java)
    }
    override fun getSize(view: View?): Vector3 {
        return Vector3(0.2f, 0.2f, 0.2f)
    }

    fun getBoxCorners(arFragment: ArFragment): List<Point> {
        var pos = transformableNode?.localPosition
        var tl = transformableNode?.localToWorldPoint(Vector3(
            pos!!.x - 0.3f,
            pos!!.y ,
            pos!!.z - 0.3f
        ))
        var tr = transformableNode?.localToWorldPoint(Vector3(
            pos!!.x + 0.3f,
            pos!!.y,
            pos!!.z - 0.3f
        ))
        var br = transformableNode?.localToWorldPoint(Vector3(
            pos!!.x + 0.3f,
            pos!!.y,
            pos!!.z + 0.3f
        ))
        var bl = transformableNode?.localToWorldPoint(Vector3(
            pos!!.x - 0.3f,
            pos!!.y,
            pos!!.z  + 0.3f
        ))
        placeBoundingPoint(tl!!,arFragment,getTopMostPlane(getPlanes(arFragment)!!.first()), "tl")
        placeBoundingPoint(tr!!,arFragment,getTopMostPlane(getPlanes(arFragment)!!.first()), "tr")
        placeBoundingPoint(bl!!,arFragment,getTopMostPlane(getPlanes(arFragment)!!.first()), "bl")
        placeBoundingPoint(br!!,arFragment,getTopMostPlane(getPlanes(arFragment)!!.first()), "br")

        return listOf(
            Point(ImageUtils.getScreenCoordinate(arFragment, tl!!)),
            Point(ImageUtils.getScreenCoordinate(arFragment, tr!!)),
            Point(ImageUtils.getScreenCoordinate(arFragment, bl!!)),
            Point(ImageUtils.getScreenCoordinate(arFragment, br!!))

            )
    }
}