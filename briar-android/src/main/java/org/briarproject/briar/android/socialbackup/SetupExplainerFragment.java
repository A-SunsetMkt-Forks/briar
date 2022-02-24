package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

public class SetupExplainerFragment extends BaseFragment {

	public static final String TAG =
			SetupExplainerFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SocialBackupSetupViewModel viewModel;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(SocialBackupSetupViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable
			ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view =
				inflater.inflate(R.layout.fragment_social_backup_setup_explainer,
						container, false);

		Button button = view.findViewById(R.id.button);
		button.setOnClickListener(e -> viewModel.onExplainerDismissed());
		return view;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}
}
