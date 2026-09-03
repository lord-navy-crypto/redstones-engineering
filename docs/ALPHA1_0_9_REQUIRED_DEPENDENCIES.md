# Alpha 1.0.9 Required Dependency Platform

RSE Alpha 1.0.9 intentionally requires five ecosystem libraries because the project will build directly on their capabilities instead of maintaining duplicate in-house infrastructure.

- JEI: recipe/use browsing and engineering progression UI.
- Jade: engineering HUD and server-backed diagnostics.
- GeckoLib: articulated engineering animation.
- Cloth Config: configuration screens and tuning UI.
- Fusion: connected textures and advanced engineering visuals.

The libraries are declared as normal Gradle dependencies and as `type="required"` NeoForge dependencies on the appropriate runtime side. They are not shaded into the RSE jar.
