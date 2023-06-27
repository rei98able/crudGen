package com.crudgen.plugin.service;

import com.intellij.database.psi.DbTable;

import java.io.IOException;

public interface UtilService {
    void generateEntity(DbTable table) throws IOException;

    void generateRepo(DbTable table) throws IOException;

    void generateServiceInterface(String name) throws IOException;

    void generateServiceInterfaceImpl(String name) throws IOException;

    void generateController(String name) throws IOException;

    String transformTableNameIntoEntityName(String name);
}
