package com.mcwb.client.input;

import java.util.Collection;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import com.google.gson.annotations.SerializedName;
import com.mcwb.client.MCWBClient;
import com.mcwb.client.input.Key.KeyCategory;
import com.mcwb.client.player.PlayerPatchClient;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.common.load.BuildableMeta;
import com.mcwb.common.meta.IMeta;
import com.mcwb.common.pack.IContentProvider;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class KeyBind extends BuildableMeta implements IKeyBind
{
	public static final BuildableLoader< IMeta >
		LOADER = new BuildableLoader<>( "key_bind", ExternalKeyBind.class );
	
	@SerializedName( value = "keyCode", alternate = { "key", "defaultKey" } )
	protected int keyCode = Keyboard.KEY_NONE;
	
	@SerializedName( value = "category", alternate = "group" )
	protected String category = KeyCategory.OTHER;
	
	/**
	 * Whether this key is down or not
	 */
	public transient boolean down = false;
	
	protected transient KeyBinding keyBind;
	
	protected KeyBind() { }
	
	KeyBind( String name, String category, int keyCode ) { this( name, category, keyCode, null ); }
	
	// This public is for develop helper
	public KeyBind(
		String name,
		String category,
		int keyCode,
		@Nullable Collection< IKeyBind > updateGroup
	) {
		this.keyCode = keyCode;
		this.category = category;
		
		this.build( name, MCWBClient.MOD );
		
		/// Assign update group
		if( updateGroup != null ) updateGroup.add( this );
		else switch( category )
		{
		case KeyCategory.MODIFY:
			InputHandler.GLOBAL_KEYS.add( this );
			break;
			
		case KeyCategory.ASSIST:
			InputHandler.CO_KEYS.add( this );
			break;
			
		case KeyCategory.GENERAL:
		case KeyCategory.GUN:
		case KeyCategory.OTHER:
			InputHandler.INCO_KEYS.add( this );
			break;
			
		default: // Should never happen
			throw new RuntimeException(
				"Unexpected key category <" + this.category + "> from <" + this.name + ">"
			);
		}
	}
	
	@Override
	public IMeta build( String name, IContentProvider provider )
	{
		super.build( name, provider );
		
		IKeyBind.REGISTRY.regis( this );
		
		this.keyBind = new KeyBinding(
			"mcwb.key." + this.name, // Add a prefix to avoid conflict
			this.keyCode,
			this.category
		);
		ClientRegistry.registerKeyBinding( this.keyBind );
		
		// Clear key code to avoid key conflict
		this.keyBind.setKeyCode( Keyboard.KEY_NONE );
		return this;
	}
	
	@Override
	public String category() { return this.category; }
	
	@Override
	public void update( boolean down )
	{
		if( down ^ this.down )
		{
			this.down = down;
			if( down ) this.onFire();
			else this.onRelease();
		}
	}
	
	@Override
	public void inactiveUpdate( boolean down )
	{
		if( !down && this.down )
		{
			this.down = false;
			this.onRelease();
		}
	}
	
	@Override
	public void restoreMcKeyBind() { this.keyBind.setKeyCode( this.keyCode ); }
	
	@Override
	public boolean clearMcKeyBind()
	{
		final int code = this.keyBind.getKeyCode();
		if( code == this.keyCode ) return false;
		
		this.keyCode = code;
		this.keyBind.setKeyCode( Keyboard.KEY_NONE );
		return true;
	}
	
	@Override
	public int keyCode() { return this.keyCode; }
	
	@Override
	public void $keyCode( int code ) { this.keyCode = code; }
	
	@Override
	public boolean down() { return this.down; }
	
	/**
	 * In default just pass the key press notification to player
	 */
	protected void onFire() { PlayerPatchClient.instance.onKeyPress( this ); }
	
	protected void onRelease() { PlayerPatchClient.instance.onKeyRelease( this ); }
	
	@Override
	protected IMeta loader() { return LOADER; }
}
