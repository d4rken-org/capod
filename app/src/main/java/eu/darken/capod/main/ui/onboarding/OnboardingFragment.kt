package eu.darken.capod.main.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.EdgeToEdgeHelper
import eu.darken.capod.common.PrivacyPolicy
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.uix.Fragment3
import eu.darken.capod.common.viewbinding.viewBinding
import eu.darken.capod.databinding.OnboardingFragmentBinding
import javax.inject.Inject


@AndroidEntryPoint
class OnboardingFragment : Fragment3(R.layout.onboarding_fragment) {

    override val vm: OnboardingFragmentVM by viewModels()
    override val ui: OnboardingFragmentBinding by viewBinding()

    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true, top = true, bottom = true)
        }
        ui.goPrivacyPolicy.setOnClickListener { webpageTool.open(PrivacyPolicy.URL) }
        ui.continueAction.setOnClickListener { vm.finishOnboarding() }
        super.onViewCreated(view, savedInstanceState)
    }
}
