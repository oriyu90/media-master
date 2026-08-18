package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import com.example.FileViewModel
import com.example.SortOption
import com.example.ViewMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun SortViewMenu(
    viewModel: FileViewModel, 
    onSelectAll: (() -> Unit)? = null,
    isCategory: Boolean = false,
    showExcludedToggle: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSort by viewModel.sortOption.collectAsStateWithLifecycle()
    val currentViewMode by (if (isCategory) viewModel.categoryViewMode else viewModel.viewMode).collectAsStateWithLifecycle()
    val showExcluded by viewModel.showExcludedInManage.collectAsStateWithLifecycle()

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onSelectAll != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_all)) },
                    onClick = {
                        expanded = false
                        onSelectAll()
                    }
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(menuLabel(R.string.sort_by_name, currentSort == SortOption.NAME)) },
                onClick = { viewModel.setSortOption(SortOption.NAME); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(menuLabel(R.string.sort_by_date, currentSort == SortOption.DATE_CREATED)) },
                onClick = { viewModel.setSortOption(SortOption.DATE_CREATED); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(menuLabel(R.string.sort_by_size, currentSort == SortOption.SIZE)) },
                onClick = { viewModel.setSortOption(SortOption.SIZE); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(menuLabel(R.string.sort_by_type, currentSort == SortOption.TYPE)) },
                onClick = { viewModel.setSortOption(SortOption.TYPE); expanded = false }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(if (currentViewMode == ViewMode.LIST) R.string.switch_to_grid else R.string.switch_to_list)) },
                onClick = {
                    if (isCategory) {
                        viewModel.setCategoryViewMode(if (currentViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                    } else {
                        viewModel.setViewMode(if (currentViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                    }
                    expanded = false
                }
            )
            if (showExcludedToggle) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(if (showExcluded) R.string.hide_excluded else R.string.show_excluded)) },
                    onClick = {
                        viewModel.toggleShowExcludedInManage()
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun menuLabel(labelRes: Int, selected: Boolean): String {
    val label = stringResource(labelRes)
    return if (selected) "$label (${stringResource(R.string.current)})" else label
}
