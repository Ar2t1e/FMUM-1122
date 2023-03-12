package com.mcwb.common.operation;

import com.mcwb.common.item.IEquippedItem;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * <p> Represents something that the player is doing with the item in main hand. </p>
 * 
 * <p> In default your implementation should guarantee that {@link #terminate()} is called whenever
 * the operation is left for execution. </p>
 * 
 * @author Giant_Salted_Fish
 */
public interface IOperation
{
	/**
	 * Placeholder operation instance. Represents that no operation is currently running.
	 */
	public static final IOperation NONE = new IOperation()
	{
		@Override
		public IOperation onStackUpdate( IEquippedItem< ? > newEquipped ) { return this; }
		
		@Override
		public String toString() { return "Operation::NONE"; }
	};
	
	public default float progress() { return 1F; }
	
	@SideOnly( Side.CLIENT )
	public default float getProgress( float smoother ) { return 1F; }
	
	/**
	 * Prepare for the execution
	 */
	public default IOperation launch( IOperation oldOp ) { return this; }
	
	/**
	 * For progressive operation that can reverse its progress. For example you can enter sprint
	 * stance and keep it then quit it after a while.
	 */
	public default IOperation toggle() { return this; }
	
	/**
	 * Called when the outer requires to terminate this operation. Be aware that the executing
	 * operation may refuse to terminate.
	 * 
	 * @return {@link #NONE} if execution is aborted
	 */
	public default IOperation terminate() { return NONE; }
	
	/**
	 * Tick this operation
	 * 
	 * @return {@link #NONE} if this operation has complete. {@code this} otherwise.
	 */
	public default IOperation tick() { return this; }
	
	/**
	 * Called when another operation is requesting to launch during the execution of this operation.
	 * You can call {@link IOperation#launch(IOperation)} on the new operation if this operation
	 * should give up its execution and let new operation to run.
	 * 
	 * @param op New operation that requests to launch
	 * @return {@link IOperation} that should be executed after this call
	 */
	public default IOperation onOtherTryLaunch( IOperation op ) { return op.launch( this ); }
	
	/**
	 * Called when player swap the item in hands
	 * 
	 * @return {@link #NONE} if this operation should terminate on hand swap
	 */
	public default IOperation onSwapHand( IEquippedItem< ? > newEquipped ) {
		return this.terminate();
	}
	
	/**
	 * Called when the item in player's main hand has changed
	 * 
	 * @return {@link #NONE} if this operation should terminate on item switch
	 */
	public default IOperation onItemChange( IEquippedItem< ? > newEquipped ) {
		return this.terminate();
	}
	
	/**
	 * Called when the corresponding stack of the current item has been updated
	 * 
	 * @return {@link #NONE} if this operation should terminate on stack update
	 */
	public IOperation onStackUpdate( IEquippedItem< ? > newEquipped );
}
