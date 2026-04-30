package com.find.gang.app.widget

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class FragmentAdapter(fragmentActivity: FragmentActivity, private val fragments: List<Fragment>) : FragmentStateAdapter(fragmentActivity) {

  override fun getItemCount(): Int = fragments.size

  override fun createFragment(position: Int): Fragment = fragments[position]
}