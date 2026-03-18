package com.openclaw.agent.ui.skills

import androidx.lifecycle.ViewModel
import com.openclaw.agent.core.skill.Skill
import com.openclaw.agent.core.skill.SkillEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val skillEngine: SkillEngine
) : ViewModel() {

    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills

    private val _selectedSkill = MutableStateFlow<Skill?>(null)
    val selectedSkill: StateFlow<Skill?> = _selectedSkill

    fun loadSkills() {
        _skills.value = skillEngine.getAllSkills()
    }

    fun selectSkill(skill: Skill) {
        _selectedSkill.value = skill
    }

    fun goBack() {
        _selectedSkill.value = null
    }

    fun toggleSkill(name: String, enabled: Boolean) {
        skillEngine.setSkillEnabled(name, enabled)
        _skills.value = skillEngine.getAllSkills()
    }

    fun reloadSkills() {
        skillEngine.reload()
        _skills.value = skillEngine.getAllSkills()
    }
}
