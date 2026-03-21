package org.vestifeed.auth

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import org.vestifeed.R
import org.vestifeed.databinding.FragmentAuthBinding
import org.vestifeed.di.Di
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.navigation.Activity

class AuthFragment : Fragment() {

    private val model: AuthModel by lazy { Di.getViewModel(AuthModel::class.java) }

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.initButtons()
    }

    override fun onResume() {
        super.onResume()
        (binding.icon.drawable as? Animatable)?.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun FragmentAuthBinding.initButtons() {
        useStandaloneBackend.setOnClickListener {
            binding.root.animate().alpha(0f).withEndAction {
                model.setStandaloneBackend()

                parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        EntriesFragment::class.java,
                        bundleOf("filter" to EntriesFilter.NotBookmarked),
                    )
                }

                (activity as Activity).binding.bottomNav.isVisible = true
            }
        }

        useMinifluxBackend.setOnClickListener {
            parentFragmentManager.commit {
                replace<MinifluxAuthFragment>(R.id.fragmentContainerView)
                addToBackStack(null)
            }
        }

        useNextcloudBackend.setOnClickListener {
            TODO()
        }
    }
}