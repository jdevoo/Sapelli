package uk.ac.ucl.excites.sapelli.collector.media;

import uk.ac.ucl.excites.sapelli.collector.control.CollectorController;
import uk.ac.ucl.excites.sapelli.collector.model.Form.AudioFeedback;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ChoiceField;
import uk.ac.ucl.excites.sapelli.collector.ui.animation.ViewAnimator;
import uk.ac.ucl.excites.sapelli.collector.ui.items.Item;
import android.view.View;

/**
 * Controller for the AudioFeedback Feature
 * 
 * @author Michalis Vitos
 *
 */
public class AudioFeedbackController
{
	private static CollectorController controller;

	public AudioFeedbackController(CollectorController controller)
	{
		AudioFeedbackController.controller = controller;
	}

	/**
	 * Speaks aloud the description of a ChoiceField and animates the ChoiceField. The description used can be either audio file or text that uses Android's TTS
	 * (Text-To-Speech) engine to read it aloud which is defined in the XML of a project.
	 * 
	 * @param choice
	 * @param choiceView
	 */
	public void playAnswer(ChoiceField choice, View choiceView)
	{
		// Check whether AudioFeedback is supported for the current form
		AudioFeedback audioFeedback = controller.getCurrentForm().getAudioFeedback();

		if(audioFeedback != null)
		{
			switch(audioFeedback)
			{
			case LONG_CLICK:
			case SEQUENTIAL:

				// If the choice has an audio, pass that audio to the Media Player
				if(choice.hasAudioAnswerDesc())
					controller.audioToVoice(controller.getProject().getSoundFolderPath() + choice.getAnswerDesc());
				else if(choice.getAnswerDesc() != null)
					// Enable TTS Audio Feedback
					controller.textToVoice(choice.getAnswerDesc());
				else
					// Enable TTS Audio Feedback
					controller.textToVoice(choice.getAltText());
				break;

			case NONE:
				controller.addLogLine("LONG_CLICK", "LongClick on " + choice.getAltText() + " but AudioFeedback is disabled");
				return;
			}

			// Apply an alpha animation to the long pressed view
			ViewAnimator.shakeAnimation(choiceView);
		}
	}

	/**
	 * Speaks aloud the description of a ControlItem and animates the ControlItem. The description used can be either audio file or text that uses Android's TTS
	 * (Text-To-Speech) engine to read it aloud which is defined in the XML of a project.
	 * 
	 * @param controlItem
	 * @param controlView
	 */
	public void playAnswer(Item controlItem, View controlView)
	{
		// Check whether AudioFeedback is supported for the current form
		AudioFeedback audioFeedback = controller.getCurrentForm().getAudioFeedback();

		if(audioFeedback != null)
		{
			switch(audioFeedback)
			{
			case LONG_CLICK:
			case SEQUENTIAL:

				// TODO: decide TTS / MediaPlayer

				// If the choice has an audio, pass that audio to the Media Player
				if(false)
					return;
				else
					// Enable TTS Audio Feedback
					controller.textToVoice(controlItem.getDescription());
				break;

			case NONE:
				controller.addLogLine("LONG_CLICK", "LongClick on " + controlItem.getDescription() + " but AudioFeedback is disabled");
				return;
			}

			// Apply an alpha animation to the long pressed view
			ViewAnimator.shakeAnimation(controlView);
		}
	}

	/**
	 * Speaks aloud the description of a ChoiceField used to ask the question. The description used can be either audio file or text that uses Android's TTS
	 * (Text-To-Speech) engine to read it aloud which is defined in the XML of a project.
	 * 
	 * @param choice
	 */
	public void playQuestion(ChoiceField choice)
	{
		// Check whether AudioFeedback is supported for the current form
		AudioFeedback audioFeedback = controller.getCurrentForm().getAudioFeedback();

		if(audioFeedback != null)
		{
			switch(audioFeedback)
			{
			case LONG_CLICK:

				// If the choice has an audio, pass that audio to the Media Player
				if(choice.hasQuestionDesc())
					controller.audioToVoice(controller.getProject().getSoundFolderPath() + choice.getQuestionDesc());
				else if(choice.getQuestionDesc() != null)
					// Enable TTS Audio Feedback
					controller.textToVoice(choice.getQuestionDesc());
				break;

			case SEQUENTIAL:

				// TODO Sequentially navigate through the items

				break;

			case NONE:
				break;
			}
		}
	}
}
