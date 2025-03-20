package com.github.skoch13.pnpmcataloglens

import com.github.skoch13.pnpmcataloglens.services.PnpmWorkspaceService
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Tests for the PNPM catalog lens functionality.
 */
class PnpmCatalogTest : BasePlatformTestCase() {
    private lateinit var workspaceFile: VirtualFile
    private lateinit var packageJsonFile: VirtualFile
    private lateinit var tempDir: File

    public override fun setUp() {
        super.setUp()

        // Create a temporary directory for the test
        tempDir = createTempDirectory().toFile()

        // Create a pnpm-workspace.yaml file
        val workspaceYaml = """
            packages:
              - packages/*

            catalog:
              react: ^18.3.1
              redux: ^5.0.1

            catalogs:
              react17:
                react: ^17.0.2
                react-dom: ^17.0.2

              react18:
                react: ^18.2.0
                react-dom: ^18.2.0
        """.trimIndent()

        val workspaceYamlFile = File(tempDir, "pnpm-workspace.yaml")
        workspaceYamlFile.writeText(workspaceYaml)

        // Create a package.json file with catalog references
        val packageJson = """
            {
              "name": "@example/app",
              "dependencies": {
                "react": "catalog:",
                "redux": "catalog:",
                "react-dom": "catalog:react17"
              }
            }
        """.trimIndent()

        val packagesDir = File(tempDir, "packages")
        packagesDir.mkdirs()

        val exampleAppDir = File(packagesDir, "example-app")
        exampleAppDir.mkdirs()

        val packageJsonFile = File(exampleAppDir, "package.json")
        packageJsonFile.writeText(packageJson)

        // Refresh the virtual file system to see the new files
        LocalFileSystem.getInstance().refresh(false)

        // Get the virtual files
        workspaceFile = LocalFileSystem.getInstance().findFileByIoFile(workspaceYamlFile)!!
        this.packageJsonFile = LocalFileSystem.getInstance().findFileByIoFile(packageJsonFile)!!
    }

    public override fun tearDown() {
        // Clean up temporary files
        tempDir.deleteRecursively()
        super.tearDown()
    }

    /**
     * Tests that the PnpmWorkspaceService correctly parses the pnpm-workspace.yaml file.
     */
    fun testPnpmWorkspaceService() {
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // Set the workspace file manually for testing
        val field = PnpmWorkspaceService::class.java.getDeclaredField("workspaceFile")
        field.isAccessible = true
        field.set(pnpmWorkspaceService, workspaceFile)

        // Parse the workspace file
        pnpmWorkspaceService.parsePnpmWorkspace()

        // Check the default catalog
        val defaultCatalog = pnpmWorkspaceService.getDefaultCatalog()
        assertNotNull("Default catalog should not be null", defaultCatalog)
        assertEquals("Default catalog should have 2 entries", 2, defaultCatalog!!.size)
        assertEquals("React version should be ^18.3.1", "^18.3.1", defaultCatalog["react"])
        assertEquals("Redux version should be ^5.0.1", "^5.0.1", defaultCatalog["redux"])

        // Check the named catalogs
        val namedCatalogs = pnpmWorkspaceService.getNamedCatalogs()
        assertNotNull("Named catalogs should not be null", namedCatalogs)
        assertEquals("There should be 2 named catalogs", 2, namedCatalogs!!.size)

        // Check the react17 catalog
        val react17Catalog = namedCatalogs["react17"]
        assertNotNull("react17 catalog should not be null", react17Catalog)
        assertEquals("react17 catalog should have 2 entries", 2, react17Catalog!!.size)
        assertEquals("React version in react17 catalog should be ^17.0.2", "^17.0.2", react17Catalog["react"])
        assertEquals("React DOM version in react17 catalog should be ^17.0.2", "^17.0.2", react17Catalog["react-dom"])

        // Check the react18 catalog
        val react18Catalog = namedCatalogs["react18"]
        assertNotNull("react18 catalog should not be null", react18Catalog)
        assertEquals("react18 catalog should have 2 entries", 2, react18Catalog!!.size)
        assertEquals("React version in react18 catalog should be ^18.2.0", "^18.2.0", react18Catalog["react"])
        assertEquals("React DOM version in react18 catalog should be ^18.2.0", "^18.2.0", react18Catalog["react-dom"])
    }

    /**
     * Tests that the PnpmWorkspaceService correctly resolves catalog references.
     */
    fun testResolveCatalogReferences() {
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // Set the workspace file manually for testing
        val field = PnpmWorkspaceService::class.java.getDeclaredField("workspaceFile")
        field.isAccessible = true
        field.set(pnpmWorkspaceService, workspaceFile)

        // Parse the workspace file
        pnpmWorkspaceService.parsePnpmWorkspace()

        // Test resolving catalog references
        assertEquals("Should resolve react from default catalog", "^18.3.1", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:"))
        assertEquals("Should resolve redux from default catalog", "^5.0.1", pnpmWorkspaceService.resolveCatalogVersion("redux", "catalog:"))
        assertEquals("Should resolve react from react17 catalog", "^17.0.2", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:react17"))
        assertEquals("Should resolve react-dom from react17 catalog", "^17.0.2", pnpmWorkspaceService.resolveCatalogVersion("react-dom", "catalog:react17"))
        assertEquals("Should resolve react from react18 catalog", "^18.2.0", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:react18"))
        assertEquals("Should resolve react-dom from react18 catalog", "^18.2.0", pnpmWorkspaceService.resolveCatalogVersion("react-dom", "catalog:react18"))

        // Test resolving non-existent catalog references
        assertNull("Should return null for non-existent package", pnpmWorkspaceService.resolveCatalogVersion("non-existent", "catalog:"))
        assertNull("Should return null for non-existent catalog", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:non-existent"))
    }
}
