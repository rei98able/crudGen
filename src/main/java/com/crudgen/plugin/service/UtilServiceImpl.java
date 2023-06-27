package com.crudgen.plugin.service;

import com.intellij.database.model.DasColumn;
import com.intellij.database.psi.DbTable;
import com.intellij.database.util.DasUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.webSymbols.utils.NameCaseUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class UtilServiceImpl implements UtilService {

    public static final String MODEL_PACKAGE_NAME = "model";
    public static final String REPOSITORY_PACKAGE_NAME = "repository";
    public static final String SERVICE_PACKAGE_NAME = "service";
    public static final String CONTROLLER_PACKAGE_NAME = "controller";
    public static final String JAVA_CLASS_FORMAT = ".java";
    private static final Logger LOG = Logger.getInstance(UtilServiceImpl.class);

    private final Project project;

    public UtilServiceImpl(Project project) {
        this.project = project;
    }

    @Override
    public void generateEntity(DbTable table) throws IOException {
        PsiDirectory psiDirectory = createPackage(MODEL_PACKAGE_NAME).getDirectories()[0];
        PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(project);
        String transformedEntityName = NameCaseUtils.toPascalCase(table.getName() + "Entity");
        String className = transformedEntityName + JAVA_CLASS_FORMAT;

        String classTemplate = generateEntityClassTemplate(table, transformedEntityName);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            psiDirectory.add(psiFileFactory.createFileFromText(className, JavaFileType.INSTANCE, classTemplate));
        });
    }

    private String generateEntityClassTemplate(DbTable table, String transformedEntityName) {
        StringBuilder template = new StringBuilder(
                String.format("""
                                import lombok.AllArgsConstructor;
                                import lombok.Builder;
                                import lombok.Data;
                                import lombok.NoArgsConstructor;
                                        
                                import javax.persistence.*;
                                @Data
                                @Builder
                                @NoArgsConstructor
                                @AllArgsConstructor
                                @Entity
                                @Table(name = "%s")
                                public class %s {
                                        """,
                        table.getName(),
                        transformedEntityName));
        DasUtil.getColumns(table).forEach(column -> {
            template.append("\n");

            template.append(generateField(column));
        });

        template.append("\n}");
        return template.toString();
    }

    private String generateField(DasColumn column) {
        if (DasUtil.isPrimary(column)) {
            if (column.getDasType().getSpecification().equals("varchar")) {
                return String.format("""
                        @Id
                        @Column(name = "%s")
                        private String %s;
                        """, column.getName(), NameCaseUtils.toCamelCase(column.getName())
                );
            } else {
                return String.format(
                        """
                                @Id
                                @GeneratedValue(strategy = GenerationType.IDENTITY)
                                private Long %s;
                                """, NameCaseUtils.toCamelCase(column.getName())
                );
            }
        }
        return switch (column.getDasType().getSpecification()) {
            case "integer" -> generateFieldText("Integer", column);
            case "bigint" -> generateFieldText("Long", column);
            case "date" -> generateFieldText("Date", column);
            case "timestamp", "timestampz" -> generateFieldText("Timestamp", column);
            default -> generateFieldText("String", column);
        };
    }

    private static String generateFieldText(String javaType, DasColumn column) {
        return String.format(
                """
                        @Column(name = "%s")
                        private %s %s;
                        """, column.getName(), javaType, NameCaseUtils.toCamelCase(column.getName()));
    }

    @Override
    public void generateRepo(DbTable table) throws IOException {
        PsiDirectory psiDirectory = createPackage(REPOSITORY_PACKAGE_NAME).getDirectories()[0];
        PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(project);
        String transformedRepositoryName = NameCaseUtils.toPascalCase(table.getName() + "Repository");
        String className = transformedRepositoryName + JAVA_CLASS_FORMAT;

        String classTemplate = generateRepositoryTemplate(table, transformedRepositoryName);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            psiDirectory.add(psiFileFactory.createFileFromText(className, JavaFileType.INSTANCE, classTemplate));
        });
    }

    private String generateRepositoryTemplate(DbTable table, String transformedRepositoryName) {
        String pkType = DasUtil
                .getColumns(table)
                .toStream()
                .filter(DasUtil::isPrimary)
                .peek(p -> LOG.debug(p.getDasType().getSpecification()))
                .map(p -> p.getDasType().getSpecification().equals("bigint") ||
                        p.getDasType().getSpecification().equals("integer") ? "Long" : "String")
                .findFirst().orElseThrow();
        return String.format(
                """
                        import org.springframework.data.jpa.repository.JpaRepository;
                                       
                        public interface %s extends JpaRepository<%s, %s> {}
                        """,
                transformedRepositoryName,
                NameCaseUtils.toPascalCase(table.getName()) + "Entity",
                pkType);
    }

    @Override
    public void generateServiceInterface(String name) throws IOException {
        PsiDirectory psiDirectory = createPackage(SERVICE_PACKAGE_NAME).getDirectories()[0];
        PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(project);
        String className = name + "Service" + JAVA_CLASS_FORMAT;

        String classTemplate = generateServiceTemplate(name);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            psiDirectory.add(psiFileFactory.createFileFromText(className, JavaFileType.INSTANCE, classTemplate));
        });
    }

    private String generateServiceTemplate(String name) {
        return String
                .format(
                        """
                                import java.util.List;
                                                        
                                public interface %2$s {
                                                        
                                    List<%1$s> findAll();
                                    
                                    %1$s findById(Long id);
                                    
                                    %1$s save(%1$s entity);
                                    
                                    %1$s update(%1$s entity);
                                    
                                    void deleteById(Long id);
                                }
                                """,
                        name + "Entity",
                        name + "Service"
                );
    }

    @Override
    public void generateServiceInterfaceImpl(String name) throws IOException {
        PsiDirectory psiDirectory = createPackage(SERVICE_PACKAGE_NAME).getDirectories()[0];
        PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(project);
        String className = name + "ServiceImpl" + JAVA_CLASS_FORMAT;

        String classTemplate = generateServiceImplTemplate(name);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            psiDirectory.add(psiFileFactory.createFileFromText(className, JavaFileType.INSTANCE, classTemplate));
        });
    }

    private String generateServiceImplTemplate(String name) {
        return String.format(
                """
                        import org.springframework.stereotype.Service;
                        import lombok.RequiredArgsConstructor;
                        import org.modelmapper.ModelMapper;
                        import java.util.List;
                                                
                        @Service
                        @RequiredArgsConstructor
                        public class %1$s implements %2$s {
                             
                             private final ModelMapper modelMapper;
                             
                             private final %3$s repository;
                             
                             @Transactional(readOnly = true)
                             @Override
                             public List<%4$s> findAll() {
                                return repository.findAll();
                             }
                             
                             @Transactional(readOnly = true)
                             @Override
                             public %4$s findById(Long id) {
                                return repository.findById(id).orElseThrow();
                             }
                             
                             @Transactional
                             @Override
                             public %4$s save(%4$s entity){
                                return repository.save(entity);
                             }
                             
                             @Transactional
                             @Override
                             public %4$s update(%4$s entity){
                                %4$s entityFromDb = repository.findById(entity.getId()).orElseThrow();
                                modelMapper.map(entity, entityFromDb);
                                return repository.saveAmdFlush(entityFromDb);
                             }
                             
                             @Transactional
                             @Override
                             public void deleteById(Long id) {
                                repository.deleteById(id);
                             }
                        }
                        """,
                name + "ServiceImpl",
                name + "Service",
                name + "Repository",
                name + "Entity");
    }

    @Override
    public void generateController(String name) throws IOException {
        PsiDirectory psiDirectory = createPackage(CONTROLLER_PACKAGE_NAME).getDirectories()[0];
        PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(project);
        String className = name + "Controller" + JAVA_CLASS_FORMAT;

        String classTemplate = generateControllerTemplate(name);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            psiDirectory.add(psiFileFactory.createFileFromText(className, JavaFileType.INSTANCE, classTemplate));
        });
    }

    private String generateControllerTemplate(String name) {
        return String.format(
                """
                        import lombok.RequiredArgsConstructor;
                        import org.springframework.web.bind.annotation.*;
                        import java.util.List;
                                        
                        @CrossOrigin(origins = "*", allowedHeaders = "*")
                        @RequestMapping("%1$s")
                        @RequiredArgsConstructor
                        @RestController
                        public class %2$s {
                             
                             private final %3$s service;
                             
                             @Operation(summary = "Find all records")
                             @GetMapping
                             public List<%4$s> findAll(){
                                return service.findAll();
                             }
                             
                             @Operation(summary = "Find by given id")
                             @GetMapping("{id}")
                             public %4$s findById(@PathVariable Long id){
                                return service.findById(id);
                             }
                             
                             @Operation(summary = "Save")
                             @PostMapping
                             public %4$s save (@RequestBody %4$s entity){
                                return service.save(entity);
                             }
                             
                             @Operation(summary = "Update")
                             @PatchMapping
                             public %4$s update(@RequestBody %4$s entity){
                                return service.update(entity);
                             }
                             
                             @Operation(summary = "Delete")
                             @DeleteMapping("{id}")
                             public void deleteById(@PathVariable Long id){
                                service.deleteById(id);
                             }
                             
                        }
                                """,
                name.toLowerCase(),
                name + "Controller",
                name + "Service",
                name + "Entity");
    }

    @Override
    public String transformTableNameIntoEntityName(String name) {
        return NameCaseUtils.toPascalCase(name);
    }

    private PsiPackage createPackage(String packageName) throws IOException {
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiPackage psiPackage = psiFacade.findPackage(packageName);

        if (psiPackage == null) {
            PsiDirectory baseDir = psiPackage.getDirectories()[0];
            if(baseDir == null){
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("CrudGen")
                        .createNotification("Cannot Find A SpringBootApplication class!", NotificationType.ERROR)
                        .notify(project);
                throw new RuntimeException("Cannot find base dir");
            }

            baseDir.createSubdirectory(packageName);

            psiPackage = psiFacade.findPackage(packageName);
        }

        return psiPackage;
    }


}
