# Firefly controlled profile — visual limits, 2026-11-06

Source/rights: [asset replacement register](ASSET_REPLACEMENT_REGISTER.md). Geometry/textures remain TEMPORARY_RESTRICTED outside Git/JAR. This record concerns the local P3 acceptance fixture, not a distributable character pack.

| Item | Evidence and scope | Remaining limit |
|---|---|---|
| Body gait | Stage e161ticks, stance position/yaw, start/stop/turn/run, flat contact, shared pelvis correction; front/side views | Scripted root only. Terrain/steps/path/follow/jump are not implemented. Gait style is a procedural baseline, not imported ZZZ animations. |
| Secondary |10chains/91segments: Qz_0_{0,3,6,9,12,15}, Hf_0_{1,3,6,8}; four core spheres on head/chest/thighs | Remaining source chains keep base pose. Adjacent static strips can deform differently; long loose accessories are not all simulated. |
| Contacts | Calibrated head radius.065m/chest.10m/thigh.045m, tip radius.008m; finite cone constraints and exact feasible-direction candidates | Spheres approximate inner body cores. Zero tip penetration does not prove triangle/segment/cloth separation. No mesh self-collision. |
| Full-profile experiment |28main chains attempted; Qz_0_17 incompatible cone/proxy contact at tick6, `p34-secondary-full-profile.log` | Rejected, not silently marked supported. Needs improved chain-wide collision response/asset profile before activation. |
| Front/side appearance | Local captured sheets show connected meshes and reset to settled base. Clothes respond to contacts; hair responds to head rotation/springs | Thin layered skirt panels can show hard folds/overlap. Existing rig/material limitations remain; this is bounded physics, not a complete cloth solver. |
| Rendering | GemRender skinning/morph, source KHR_unlit material presentation | Full MToon/outline fidelity is not implemented. Sodium/Iris/version/shader results belong to P3.5. |
| Presentation | Frozen host-generated palettes, normalized model-space views | Gameplay pose streaming/dialogue/multi-client interpolation remain P3.5. |

P3.4 acceptance is limited to the explicit stable profile and diagnostics. Do not use it as evidence that every source chain or every clothing contact is supported, or that full P3 is complete.

P3.5 follow-up: gameplay host streaming/dialogue/two-client/reconnect and selected Sodium0.6.13/Iris1.8.8/Complementary r5.5.1 are verified in report003. Integrated and dedicated use the same profile. Multi run recorded14chain-local constraint resets/2108steps, active shader13/1506; whole-NPC failures0. A local reset is visible degradation, not evidence of complete collision support. P3.4's historical scope above is unchanged; full MToon/outline and full cloth remain unsupported.

Final budget multi recorded15chain-local resets/2606steps,0whole-NPC failures; report004 retains the exact acceptance limits and results.
