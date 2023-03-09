package com.mcwb.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.lwjgl.opengl.GLContext;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcwb.client.ammo.AmmoRenderer;
import com.mcwb.client.gun.CarGripRenderer;
import com.mcwb.client.gun.GripRenderer;
import com.mcwb.client.gun.GunPartRenderer;
import com.mcwb.client.gun.GunRenderer;
import com.mcwb.client.gun.MagRenderer;
import com.mcwb.client.gun.OpticSightRenderer;
import com.mcwb.client.input.InputHandler;
import com.mcwb.client.input.KeyBind;
import com.mcwb.client.item.ItemRenderer;
import com.mcwb.client.render.IAnimator;
import com.mcwb.client.render.IRenderer;
import com.mcwb.client.render.Renderer;
import com.mcwb.common.MCWB;
import com.mcwb.common.MCWBResource;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.common.load.IContentProvider;
import com.mcwb.common.load.IMeshLoadSubscriber;
import com.mcwb.common.meta.Registry;
import com.mcwb.util.Animation;
import com.mcwb.util.BoneAnimation;
import com.mcwb.util.Mat4f;
import com.mcwb.util.Mesh;
import com.mcwb.util.ObjMeshBuilder;
import com.mcwb.util.Quat4f;
import com.mcwb.util.Vec3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.discovery.ContainerType;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public final class MCWBClient extends MCWB
	implements IAutowirePlayerChat, IAutowireBindTexture, IAutowireSmoother
{
	/// *** For easy referencing *** ///
	public static final Minecraft MC = Minecraft.getMinecraft();
	public static final GameSettings SETTINGS = MC.gameSettings;
	
	public static final MCWBClient MOD = new MCWBClient();
	
	public static final Registry< BuildableLoader< ? extends IRenderer > >
		MODEL_LOADERS = new Registry<>();
	
	// TODO: mesh loaders? to support other types of model
	
	public static final String MODIFY_INDICATOR = "modify_indicator";
	
	public static byte[] modifyLoc;
	
	public static float freeViewLimitSquared;
	
	public static float camDropCycle;
	public static float camDropAmpl;
	
	public static float camDropImpact;
	
	final LinkedList< IMeshLoadSubscriber > meshLoadSubscribers = new LinkedList<>();
	
	final HashMap< String, IRenderer > rendererPool = new HashMap<>();
	
	final HashMap< String, Mesh > meshPool = new HashMap<>();
	
	/**
	 * Where key binds will be saved to
	 */
	File keyBindsFile;
	
	/**
	 * Buffered textures
	 * 
	 * TODO: clear pools after use maybe?
	 */
	private final HashMap< String, ResourceLocation > texturePool = new HashMap<>();
	
	private final HashMap< String, IAnimator > animationPool = new HashMap<>();
	
	private MCWBClient() { }
	
	@Override
	public void preLoad()
	{
		// Check OpenGL support
		if( !GLContext.getCapabilities().OpenGL30 )
			throw new RuntimeException( I18n.format( "mcwb.opengl_version_too_low" ) );
		
		final Framebuffer framebuffer = MC.getFramebuffer();
		if( !framebuffer.isStencilEnabled() && !framebuffer.enableStencil() )
			throw new RuntimeException( I18n.format( "mcwb.stencil_not_supported" ) );
		
		// Do prepare load
		super.preLoad();
		
		// Register model loaders
		MODEL_LOADERS.regis( Renderer.LOADER );
		MODEL_LOADERS.regis( GunPartRenderer.LOADER );
		MODEL_LOADERS.regis( GunRenderer.LOADER );
		MODEL_LOADERS.regis( MagRenderer.LOADER );
		MODEL_LOADERS.regis( GripRenderer.LOADER );
		MODEL_LOADERS.regis( CarGripRenderer.LOADER );
		MODEL_LOADERS.regis( OpticSightRenderer.LOADER );
		MODEL_LOADERS.regis( AmmoRenderer.LOADER );
		
		// Register default textures
		this.texturePool.put( Renderer.TEXTURE_RED.getPath(), Renderer.TEXTURE_RED );
		this.texturePool.put( Renderer.TEXTURE_GREEN.getPath(), Renderer.TEXTURE_RED );
		this.texturePool.put( Renderer.TEXTURE_BLUE.getPath(), Renderer.TEXTURE_RED );
		this.texturePool.put( ItemRenderer.TEXTURE_STEVE.getPath(), ItemRenderer.TEXTURE_STEVE );
		this.texturePool.put( ItemRenderer.TEXTURE_ALEX.getPath(), ItemRenderer.TEXTURE_ALEX );
		
		// The default NONE mesh
		this.meshPool.put( "", Mesh.NONE );
	}
	
	@Override
	public void load()
	{
		// Load key binds before the content load
		this.keyBindsFile = new File( this.gameDir, "config/mcwb-keys.json" );
		if( !this.keyBindsFile.exists() )
		{
			try { this.keyBindsFile.createNewFile(); }
			catch( IOException e ) { this.except( e, "mcwb.error_creating_key_binds_file" ); }
			InputHandler.saveTo( this.keyBindsFile );
		}
		else InputHandler.readFrom( this.keyBindsFile );
		
		// Construct a default indicator
		final JsonObject indicator = new JsonObject();
		indicator.addProperty( "creativeTab", MCWB.HIDE_TAB.name() );
		indicator.addProperty( "renderer", "renderers/modify_indicator.json" );
		indicator.addProperty( "texture", "textures/0x00ff00.png" );
		TYPE_LOADERS.get( "gun_part" ).parser.apply( indicator ).build( MODIFY_INDICATOR, this );
		
		// Do load content packs!
		super.load();
	}
	
	@Override
	public void regis( IMeshLoadSubscriber subscriber ) {
		this.meshLoadSubscribers.add( subscriber );
	}
	
	@Override
	public void addResourceDomain( File resource )
	{
		super.addResourceDomain( resource );
		
		// Register it as a resource pack to load textures and sounds
		// Reference: Flan's Mod content pack load
		final TreeMap< String, Object > descriptor = new TreeMap<>();
		descriptor.put( "modid", ID );
		descriptor.put( "name", NAME + ":" + resource.getName() );
		descriptor.put( "version", "1" ); // TODO: from pack info maybe
		final FMLModContainer container = new FMLModContainer(
			MCWB.class.getName(),
			new ModCandidate(
				resource, resource,
				resource.isDirectory() ? ContainerType.DIR : ContainerType.JAR
			),
			descriptor
		);
		container.bindMetadata( MetadataCollection.from( null, "" ) );
		FMLClientHandler.instance().addModAsResource( container );
	}
	
	/**
	 * Load .json or .class renderer from the given path.
	 * 
	 * @param path Path of the renderer to load
	 * @param fallBackType Renderer type to use if "__type__" field does not exist in ".json" file
	 * @return {@code null} if required loader does not exist or an exception was thrown
	 * TODO: maybe a null object to avoid null pointer
	 */
	@Nullable
	public IRenderer loadRenderer( String path, String fallbackType, IContentProvider provider )
	{
		return this.rendererPool.computeIfAbsent( path, key -> {
			try
			{
				if( key.endsWith( ".json" ) )
				{
					final MCWBResource identifier = new MCWBResource( key );
					try( IResource res = MC.getResourceManager().getResource( identifier ) )
					{
						final InputStreamReader in = new InputStreamReader( res.getInputStream() );
						final JsonObject obj = GSON.fromJson( in, JsonObject.class );
						
						// Try get required loader and load
						final JsonElement type = obj.get( "__type__" );
						final String entry = type != null
							? type.getAsString().toLowerCase() : fallbackType;
						final BuildableLoader< ? extends IRenderer >
							loader = MODEL_LOADERS.get( entry );
						if( loader != null )
							return loader.parser.apply( obj ).build( key, provider );
						
						throw new RuntimeException(
							this.format( "mcwb.model_loader_not_found", key, entry )
						);
					}
				}
				else if( key.endsWith( ".class" ) )
				{
					return ( IRenderer ) this.loadClass( key.substring( 0, key.length() - 6 ) )
						.getConstructor( String.class, IContentProvider.class )
							.newInstance( key, provider );
				}
				
				// Unknown renderer type
				else throw new RuntimeException( "Unsupported renderer file type" ); // TODO: format this?
			}
			catch( Exception e ) { this.except( e, "mcwb.error_loading_renderer", key ); }
			return null;
		} );
	}
	
	@Override
	public IRenderer loadRenderer( String path, String fallBackType ) {
		return this.loadRenderer( path, fallBackType, this );
	}
	
	/**
	 * @param processor Use this to process .obj model before compiling it to mesh
	 * @return {@link Mesh#NONE} if any error has occurred
	 */
	@Override
	public Mesh loadMesh( String path, Function< Mesh.Builder, Mesh.Builder > processor )
	{
		return this.meshPool.computeIfAbsent( path, key -> {
			try
			{
				// TODO: switch by suffix to support other types of models?
				if( key.endsWith( ".obj" ) )
					return processor.apply( new ObjMeshBuilder().load( key ) ).quickBuild();
//				if( key.endsWith( ".class" ) )
//					return ( Mesh ) this.loadClass( key.substring( 0, key.length() - 6 ) )
//						.getConstructor().newInstance();
				
				// Unknown mesh type
				throw new RuntimeException( "Unsupported model file type" ); // TODO: format this?
			}
			catch( Exception e ) { this.except( e, "mcwb.error_loading_mesh", key ); }
			return Mesh.NONE;
		} );
	}
	
	@Override
	public ResourceLocation loadTexture( String path ) {
		return this.texturePool.computeIfAbsent( path, key -> new MCWBResource( key ) );
	}
	
	@Override
	public IAnimator loadAnimation( String path )
	{
		return this.animationPool.computeIfAbsent( path, key -> {
			try
			{
				if( key.endsWith( ".json" ) )
				{
					// For animation exported from blockbench
					final MCWBResource identifier = new MCWBResource( key );
					try( IResource res = MC.getResourceManager().getResource( identifier ) )
					{
						final InputStreamReader in = new InputStreamReader( res.getInputStream() );
						final BBAnimationJson json = GSON.fromJson( in, BBAnimationJson.class );
						
						final Animation ani = new Animation();
						final HashMap< BBBoneJson, BoneAnimation > mapper = new HashMap<>();
						
						final float timeFactor = 1F / json.animation_length;
						final float scale = json.positionScale;
						final Mat4f mat = Mat4f.locate();
						json.bones.forEach( ( channel, bbBone ) -> {
							final BoneAnimation bone = new BoneAnimation();
							bbBone.position.forEach( ( time, pos ) -> {
								pos.z = -pos.z; // TODO: remove this
								pos.scale( scale );
								bone.pos.put( time * timeFactor, pos );
							} );
							bbBone.rotation.forEach( ( time, rot ) -> {
								mat.setIdentity();
								mat.rotateZ( -rot.z );
								mat.rotateY( -rot.y );
								mat.rotateX( rot.x );
								bone.rot.put( time * timeFactor, new Quat4f( mat ) );
							} );
							bbBone.alpha.forEach(
								( time, alpha ) -> bone.alpha.put( time * timeFactor, alpha )
							);
							bone.addGuard();
							
							ani.channels.put( channel, bone );
							mapper.put( bbBone, bone );
						} );
						mat.release();
						
						mapper.forEach( ( bbBone, bone ) -> {
							final BoneAnimation parent = ani.channels.get( bbBone.parent );
							if( parent != null ) bone.parent = parent;
							else ani.rootBones.add( bone );
						} );
						return ani;
					}
				}
				
				if( key.endsWith( ".class" ) )
					
				
				throw new RuntimeException( "Unsupported animation file type" );
			}
			catch( Exception e ) { this.except( e, "mcwb.error_loading_animation", key ); }
			return IAnimator.INSTANCE;
		} );
	}
	
	@Override
	public boolean isClient() { return true; }
	
	@Override
	public void clientOnly( Runnable task ) { task.run(); }
	
	@Override
	public String format( String translateKey, Object... parameters ) {
		return I18n.format( translateKey, parameters );
	}
	
	@Override
	protected void regisSideDependentLoaders() { TYPE_LOADERS.regis( KeyBind.LOADER ); }
	
	@Override
	protected void reloadResources() // TODO: make this part more clear?
	{
		// Force resource reload to load those in domain of content packs
		// TODO: maybe check if is only mod based content pack
		FMLClientHandler.instance().refreshResources(
			VanillaResourceType.MODELS,
			VanillaResourceType.TEXTURES,
			VanillaResourceType.SOUNDS,
			VanillaResourceType.LANGUAGES
		);
	}
	
	@Override
	protected GsonBuilder gsonBuilder()
	{
		final GsonBuilder builder = super.gsonBuilder();
		
		final JsonDeserializer< ResourceLocation > TEXTURE_ADAPTER =
			( json, typeOfT, context ) -> this.loadTexture( json.getAsString() );
		builder.registerTypeAdapter( ResourceLocation.class, TEXTURE_ADAPTER );
		
		return builder;
	}
	
	private static class BBAnimationJson
	{
//		boolean loop = false;
		float animation_length;
		Map< String, BBBoneJson > bones = Collections.emptyMap();
		
		/**
		 * Additional scale applied on animation upon load
		 */
		float positionScale = 1F;
	}
	
	private static class BBBoneJson
	{
		String parent;
		Map< Float, Vec3f > position = Collections.emptyMap();
		Map< Float, Float > alpha = Collections.emptyMap();
		
		// Notice that the euler rotation order of bb animation is actually xyz
		Map< Float, Vec3f > rotation = Collections.emptyMap();
	}
}
