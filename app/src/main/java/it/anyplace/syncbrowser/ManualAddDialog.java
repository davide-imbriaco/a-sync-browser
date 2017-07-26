package it.anyplace.syncbrowser;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Created by enrico on 26/07/17.
 */

public class ManualAddDialog extends DialogFragment {

	private EditText id = null;
	private MainActivity callback;

	public ManualAddDialog() {
	}

	public static ManualAddDialog newInstance(MainActivity callback) {
		ManualAddDialog ret = new ManualAddDialog();
		ret.callback = callback;
		return ret;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		LayoutInflater inflater = getActivity().getLayoutInflater();
		final View layout = inflater.inflate(R.layout.fragment_manual_add, null);
		this.id = (EditText) layout.findViewById(R.id.edit_text_id);

		return new AlertDialog.Builder(getActivity())
				.setView(layout)
				.setTitle(R.string.add_device_text_label)
				.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						// do nothing cause is overrided in onStart
					}
				})
				.setNegativeButton(getString(R.string.discard),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								dismiss();
							}
						})
				.create();
	}

	@Override
	public void onStart() {
		super.onStart();

		final AlertDialog alertDialog = (AlertDialog) getDialog();

//		Override positive button
		if (alertDialog != null) {
			Button positive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
			positive.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					String idtxt = id.getText().toString();
					callback.importDeviceId(idtxt);
					dismiss();
				}
			});
		}
	}
}
