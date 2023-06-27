package com.crudgen.plugin.action;

import com.crudgen.plugin.service.UtilService;
import com.crudgen.plugin.service.UtilServiceImpl;
import com.intellij.database.psi.DbTable;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenerateCrud extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        List<DbTable> selectedTables = getSelectedTables(event);
        if (selectedTables.isEmpty()) {
            return;
        }
        UtilService utilService = new UtilServiceImpl(project);

        ProgressManager.getInstance().run(new Task.Modal(project, "Generating CRUD", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                indicator.setFraction(0.0);
                int totalEntities = selectedTables.size();
                int processedEntities = 0;

                for (DbTable table: selectedTables) {
                    try {
                        String entityName = utilService.transformTableNameIntoEntityName(table.getName());

                        utilService.generateEntity(table);

                        utilService.generateRepo(table);

                        utilService.generateServiceInterface(entityName);

                        utilService.generateServiceInterfaceImpl(entityName);

                        utilService.generateController(entityName);

                        processedEntities++;

                        double progress = (double) processedEntities / totalEntities;
                        indicator.setFraction(progress);
                        indicator.setText("Processing Table " + table.getName() + "\t" + processedEntities + "/" + totalEntities);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("CrudGen")
                        .createNotification("Classes successfully generated!", NotificationType.INFORMATION)
                        .notify(project);
            }
        });

    }

    private List<DbTable> getSelectedTables(AnActionEvent event) {
        return Stream.of(Objects.requireNonNull(event.getDataContext().getData(LangDataKeys.PSI_ELEMENT_ARRAY)))
                .filter(PsiElement::isValid)
                .map(DbTable.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}
