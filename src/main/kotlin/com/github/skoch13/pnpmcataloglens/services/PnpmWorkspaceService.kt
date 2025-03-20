package com.github.skoch13.pnpmcataloglens.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.guessProjectDir
import org.yaml.snakeyaml.Yaml
import java.io.IOException

/**
 * Main service for the PNPM Catalog Lens plugin.
 * Handles pnpm-workspace.yaml files.
 * Detects if a project has a pnpm-workspace.yaml file and extracts catalog information from it.
 */
@Service(Service.Level.PROJECT)
class PnpmWorkspaceService(private val project: Project) {
    private val LOG = logger<PnpmWorkspaceService>()

    // Cache for a workspace file
    private var workspaceFile: VirtualFile? = null

    // Cache for catalog data
    private var defaultCatalog: Map<String, String>? = null
    private var namedCatalogs: Map<String, Map<String, String>>? = null

    /**
     * Checks if the project has a pnpm-workspace.yaml file.
     */
    fun hasPnpmWorkspace(): Boolean {
        return findWorkspaceFile() != null
    }

    /**
     * Finds the pnpm-workspace.yaml file in the project.
     */
    fun findWorkspaceFile(): VirtualFile? {
        if (workspaceFile != null && workspaceFile!!.isValid) {
            return workspaceFile
        }

        val projectDir = project.guessProjectDir() ?: return null
        val workspaceFile = projectDir.findChild("pnpm-workspace.yaml")

        if (workspaceFile != null && workspaceFile.exists()) {
            this.workspaceFile = workspaceFile
            return workspaceFile
        }

        return null
    }

    /**
     * Parses the pnpm-workspace.yaml file and extracts catalog information.
     */
    fun parsePnpmWorkspace() {
        val workspaceFile = findWorkspaceFile() ?: return

        try {
            val content = String(workspaceFile.contentsToByteArray())
            val yaml = Yaml()
            val data = yaml.load<Map<String, Any>>(content)

            // Parse default catalog
            if (data.containsKey("catalog") && data["catalog"] is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                defaultCatalog = data["catalog"] as Map<String, String>
            }

            // Parse named catalogs
            if (data.containsKey("catalogs") && data["catalogs"] is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val catalogsData = data["catalogs"] as Map<String, Map<String, String>>
                namedCatalogs = catalogsData
            }

            LOG.info("Parsed pnpm-workspace.yaml: defaultCatalog=${defaultCatalog?.size ?: 0} entries, namedCatalogs=${namedCatalogs?.size ?: 0} entries")
        } catch (e: IOException) {
            LOG.warn("Failed to parse pnpm-workspace.yaml", e)
            defaultCatalog = null
            namedCatalogs = null
        }
    }

    /**
     * Resolves a catalog version for a package.
     *
     * @param packageName The name of the package.
     * @param catalogRef The catalog reference (e.g. "catalog:" or "catalog:react18").
     * @return The resolved version or null if not found.
     */
    fun resolveCatalogVersion(packageName: String, catalogRef: String): String? {
        // If we haven't parsed the workspace file yet, do it now
        if (defaultCatalog == null && namedCatalogs == null) {
            parsePnpmWorkspace()
        }

        // Handle the default catalog shorthand "catalog:"
        if (catalogRef == "catalog:") {
            return defaultCatalog?.get(packageName)
        }

        // Handle named catalogs "catalog:name"
        if (catalogRef.startsWith("catalog:")) {
            val catalogName = catalogRef.substring("catalog:".length)
            return namedCatalogs?.get(catalogName)?.get(packageName)
        }

        return null
    }

    /**
     * Refreshes the catalog data by re-parsing the workspace file.
     */
    fun refresh() {
        workspaceFile = null
        defaultCatalog = null
        namedCatalogs = null
        parsePnpmWorkspace()
    }

    /**
     * Gets the default catalog.
     */
    fun getDefaultCatalog(): Map<String, String>? {
        if (defaultCatalog == null) {
            parsePnpmWorkspace()
        }
        return defaultCatalog
    }

    /**
     * Gets the named catalogs.
     */
    fun getNamedCatalogs(): Map<String, Map<String, String>>? {
        if (namedCatalogs == null) {
            parsePnpmWorkspace()
        }
        return namedCatalogs
    }

}
