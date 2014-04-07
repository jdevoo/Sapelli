package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import java.io.File;

import uk.ac.ucl.excites.sapelli.collector.control.CollectorController;
import uk.ac.ucl.excites.sapelli.collector.control.Controller.FormSession.Mode;
import uk.ac.ucl.excites.sapelli.collector.model.CollectorRecord;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ChoiceField;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import uk.ac.ucl.excites.sapelli.collector.ui.animation.PressAnimator;
import uk.ac.ucl.excites.sapelli.collector.ui.drawables.SaltireCross;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.PickerAdapter;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.PickerView;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.DrawableItem;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.EmptyItem;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.FileImageItem;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.Item;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.LayeredItem;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.TextItem;
import uk.ac.ucl.excites.sapelli.collector.util.ColourHelpers;
import uk.ac.ucl.excites.sapelli.collector.util.ScreenMetrics;
import uk.ac.ucl.excites.sapelli.shared.util.io.FileHelpers;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The UI for ChoiceFields
 * 
 * @author mstevens
 */
public class AndroidChoiceUI extends ChoiceUI<View, CollectorView>
{
	
	static public final float PAGE_CHOSEN_ITEM_SIZE_DIP = 60.0f; // width = height
	static public final float PAGE_CHOSEN_ITEM_MARGIN_DIP = 1.0f; // same margin all round
	static public final float CROSS_THICKNESS = 0.02f;
	
	private PageView pageView;
	private ChoiceView pickView;

	public AndroidChoiceUI(ChoiceField choice, CollectorController controller, CollectorView collectorView)
	{
		super(choice, controller, collectorView);
	}

	@Override
	public View getPlatformView(boolean onPage, CollectorRecord record, boolean newRecord)
	{
		if(onPage)
		{
			if(!field.isRoot())
				return null; // just in case
			
			if(pageView == null)
				pageView = new PageView(collectorUI.getContext());
			
			pageView.setEnabled(controller.getCurrentFormMode() != Mode.EDIT || field.isEditable()); // disable when in edit mode and field is not editable, otherwise enable
			
			// Update pageView:
			ChoiceField chosen = field.getSelectedChoice(record);
			if(chosen != null)
				pageView.setChosen(chosen);
			else
				pageView.setChosen(field);
			
			return pageView;
		}
		else
		{
			if(pickView == null)
				pickView = new ChoiceView(collectorUI.getContext());
			
			// Update pickView:
			pickView.update();
			
			return pickView;
		}
	}
	
	/**
	 * @author mstevens
	 *
	 */
	public class PageView extends LinearLayout implements OnClickListener, OnFocusChangeListener
	{

		private TextView label;
		private View chosenView;
		private int chosenSizePx;
		private int chosenPaddingPx;
		private int chosenMarginPx;
		
		public PageView(Context context)
		{
			super(context);
			this.setOrientation(LinearLayout.VERTICAL);
			
			// Add label:
			label = new TextView(getContext());
			label.setText(field.getLabel());
			this.addView(label);
			
			chosenSizePx = ScreenMetrics.ConvertDipToPx(context, PAGE_CHOSEN_ITEM_SIZE_DIP);
			chosenPaddingPx = ScreenMetrics.ConvertDipToPx(context, CollectorView.PADDING_DIP);
			chosenMarginPx = ScreenMetrics.ConvertDipToPx(context, PAGE_CHOSEN_ITEM_MARGIN_DIP);
		}
		
		public void setChosen(ChoiceField chosenField)
		{
			// Remove previous:
			if(chosenView != null)
				this.removeView(chosenView);
			
			// New chosenView
			chosenView = createItem(chosenField, chosenSizePx, chosenSizePx, chosenPaddingPx, !isEnabled()).getView(getContext());
			chosenView.setOnClickListener(this);
			
			// Make other fields lose focus, make keyboard disappear, and simulate clicking with onFocusChange:
			chosenView.setFocusable(true);
			chosenView.setFocusableInTouchMode(true);
			chosenView.setOnFocusChangeListener(this);

			// Set margins on layoutparams:
			LayoutParams chosenLP = new LinearLayout.LayoutParams(chosenView.getLayoutParams());
			chosenLP.setMargins(chosenMarginPx, chosenMarginPx, chosenMarginPx, chosenMarginPx);
			
			// Add the view:
			this.addView(chosenView, chosenLP);
		}

		@Override
		public void onFocusChange(View v, boolean hasFocus)
		{
			if(hasFocus)
			{
				// Hide keyboard if it is currently shown:
				collectorUI.hideKeyboard();
				
				// Lose focus again:
				v.clearFocus();
				
				// Simulate click:
				onClick(v);
			}
		}

		@Override
		public void onClick(View v)
		{
			// Do nothing if not enabled:
			if(!isEnabled())
				return;
			
			// The user will make a choice now, so don't annoy him/her with the red box:
			clearPageInvalidMark();
			
			// Task to perform after animation has finished:
			Runnable action = new Runnable()
			{
				public void run()
				{
					controller.goTo(field, true); // go to field and leave page without validation
				}
			};

			// Execute the "press" animation if allowed, then perform the action: 
			if(controller.getCurrentForm().isAnimation())
				(new PressAnimator(action, v, collectorUI)).execute(); //execute animation and the action afterwards
			else
				action.run(); //perform task now (animation is disabled)			
		}
		
	}
	
	/**
	 * TODO later we may implement colSpan/rowSpan support here, but that would require us to base the ChoiceView on GridLayout rather than PickerView/GridView
	 * 
	 * @author Julia, mstevens, Michalis Vitos
	 */
	public class ChoiceView extends PickerView implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener
	{
		
		static public final String TAG = "ChoiceView";
				
		public ChoiceView(Context context)
		{
			super(context);
			
			// UI set-up:
			setBackgroundColor(Color.BLACK);
			int spacingPx = collectorUI.getSpacingPx();
			setHorizontalSpacing(spacingPx);
			setVerticalSpacing(spacingPx);
			
			// Number of columns:
			setNumColumns(field.getCols());
			
			// Item size & padding:
			int itemWidthPx = collectorUI.getIconWidthPx(field.getCols());
			int itemHeightPx = collectorUI.getIconHeightPx(field.getRows(), controller.getControlsState().isAnyButtonShown());
			int itemPaddingPx = ScreenMetrics.ConvertDipToPx(context, CollectorView.PADDING_DIP);

			// Adapter & images:
			pickerAdapter = new PickerAdapter(context);
			for(ChoiceField child : field.getChildren())
				pickerAdapter.addItem(createItem(child, itemWidthPx, itemHeightPx, itemPaddingPx, !field.isEnabled()));
			
			// Click listeners:
			setOnItemClickListener(this);
			setOnItemLongClickListener(this);
		}
		
		public void update()
		{
			// Update visibility:
			int c = 0;
			for(ChoiceField child : field.getChildren())
				pickerAdapter.getItem(c++).setVisibility(controller.isFieldEndabled(child));
			setAdapter(pickerAdapter);
		}
		
		@Override
		public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
		{
			// Task to perform after animation has finished:
			Runnable action = new Runnable()
			{
				public void run()
				{
					choiceMade(field.getChildren().get(position)); // pass the chosen child
				}
			};

			// Execute the "press" animation if allowed, then perform the action: 
			if(controller.getCurrentForm().isAnimation())
				(new PressAnimator(action, v, collectorUI)).execute(); //execute animation and the action afterwards
			else
				action.run(); //perform task now (animation is disabled)
		}
		
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id)
		{
			return false;
		}

	}
	
	/**
	 * Creates an DictionaryItem object responding to the provided child ChoiceField
	 * 
	 * Note: if we add colSpan/rowSpan support the right itemWidth/Height would need to be computed here (e.g.: for rowSpan=2 the itemWidth becomes (itemWidth*2)+spacingPx)
	 * 
	 * @param child
	 * @return corresponding item
	 */
	private Item createItem(ChoiceField child, int itemWidthPx, int itemHeightPx, int itemPaddingPx, boolean grayedOut)
	{
		File imageFile = controller.getProject().getImageFile(child.getImageRelativePath());
		Item item = null;
		if(FileHelpers.isReadableFile(imageFile))
			item = new FileImageItem(imageFile);
		else
			item = new TextItem(child.getAltText()); //render alt text instead of image
		
		// Set background colour:
		item.setBackgroundColor(ColourHelpers.ParseColour(child.getBackgroundColor(), Field.DEFAULT_BACKGROUND_COLOR));
		
		// Crossing & graying out
		if(child.isCrossed() || grayedOut)
		{	
			LayeredItem layeredItem = new LayeredItem();
			layeredItem.addLayer(item, false);
			// Crossing:
			if(child.isCrossed())
				layeredItem.addLayer(new DrawableItem(new SaltireCross(ColourHelpers.ParseColour(child.getCrossColor(), ChoiceField.DEFAULT_CROSS_COLOR), CROSS_THICKNESS))); // later we may expose thickness in the XML as well
			// Graying-out:
			if(grayedOut)
			{
				// Make background of layered stack gray:
				layeredItem.setBackgroundColor(CollectorView.COLOR_GRAY); 			
				// Add grayed-out layer:
				Item grayOutOverlay = new EmptyItem();
				grayOutOverlay.setBackgroundColor(CollectorView.COLOR_SEMI_TRANSPARENT_GRAY);
				layeredItem.addLayer(grayOutOverlay, false);	
			}
			// Item becomes layered:
			item = layeredItem;
		}
		
		// Set size & padding:
		item.setWidthPx(itemWidthPx);
		item.setHeightPx(itemHeightPx);
		item.setPaddingPx(itemPaddingPx);
		
		return item;
	}
	
}