package com.eds.healthkit

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.eds.healthkit.databinding.FragmentLoginBinding
import kotlinx.android.synthetic.main.fragment_login.*

class LogInFragment: Fragment() {

    private lateinit var delegate: HuaweiLogInDelegate

    companion object {

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        delegate = context as HuaweiLogInDelegate
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnHuaweiIDAuth.setOnClickListener {
            signInWithHuawei()
        }
    }

    private fun signInWithHuawei() {
        delegate.signInWithHuawei()
    }
}