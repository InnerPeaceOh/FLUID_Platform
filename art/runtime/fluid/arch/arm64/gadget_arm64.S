// The below codes are dependent on stack.cc(line 822) and context_arm64.cc
// So, if you modify this codes, you should also modify the two files

.global RpcGadgetTrampoline
RpcGadgetTrampoline:
	// Push all registers
	sub sp, sp, #512
	stp x29, lr, [sp, #496]
	stp x27, x28, [sp, #480]
	stp x25, x26, [sp, #464]
	stp x23, x24, [sp, #448]
	stp x21, x22, [sp, #432]
	stp x19, x20, [sp, #416]
	stp x17, x18, [sp, #400]
	stp x15, x16, [sp, #384]
	stp x13, x14, [sp, #368]
	stp x11, x12, [sp, #352]
	stp x9, x10, [sp, #336]
	stp x7, x8, [sp, #320]
	stp x5, x6, [sp, #304]
	stp x3, x4, [sp, #288]
	stp x1, x2, [sp, #272]
	str x0, [sp, #264]
	stp d29, d30, [sp, #240]
	stp d27, d28, [sp, #224]
	stp d25, d26, [sp, #208]
	stp d23, d24, [sp, #192]
	stp d21, d22, [sp, #176]
	stp d19, d20, [sp, #160]
	stp d17, d18, [sp, #144]
	stp d15, d16, [sp, #128]
	stp d13, d14, [sp, #112]
	stp d11, d12, [sp, #96]
	stp d9, d10, [sp, #80]
	stp d7, d8, [sp, #64]
	stp d5, d6, [sp, #48]
	stp d3, d4, [sp, #32]
	stp d1, d2, [sp, #16]
	str d0, [sp, #8]
	
	add x2, sp, #512
	bl NeedRpcTranslation
	cbz x0, .OriginalGadget
	cmp x0, #1
	beq .RpcTranslationGadget
	cmp x0, #2
	beq .DummyCallGadget
	cmp x0, #3
	beq .ReturnCachingGadget
	cmp x0, #4
	beq .RpcCachingGadget
	cmp x0, #5
	beq .SkipOriginalGadget

.RpcTranslationGadget:
	add x0, sp, #264
	add x1, sp, #8
	add x2, sp, #512
	bl RpcTranslationNative

	ldr d0, [sp, #8]
	ldp d1, d2, [sp, #16]
	ldp d3, d4, [sp, #32]
	ldp d5, d6, [sp, #48]
	ldp d7, d8, [sp, #64]
	ldp d9, d10, [sp, #80]
	ldp d11, d12, [sp, #96]
	ldp d13, d14, [sp, #112]
	ldp d15, d16, [sp, #128]
	ldp d17, d18, [sp, #144]
	ldp d19, d20, [sp, #160]
	ldp d21, d22, [sp, #176]
	ldp d23, d24, [sp, #192]
	ldp d25, d26, [sp, #208]
	ldp d27, d28, [sp, #224]
	ldp d29, d30, [sp, #240]
	ldr x0, [sp, #264]
	ldp x1, x2, [sp, #272]
	ldp x3, x4, [sp, #288]
	ldp x5, x6, [sp, #304]
	ldp x7, x8, [sp, #320]
	ldp x9, x10, [sp, #336]
	ldp x11, x12, [sp, #352]
	ldp x13, x14, [sp, #368]
	ldp x15, x16, [sp, #384]
	ldp x17, x18, [sp, #400]
	ldp x19, x20, [sp, #416]
	ldp x21, x22, [sp, #432]
	ldp x23, x24, [sp, #448]
	ldp x25, x26, [sp, #464]
	ldp x27, x28, [sp, #480]
	ldp x29, lr, [sp, #496]
	add sp, sp, #512

	ldr lr, [x0, #48]
	blr lr

	sub sp, sp, #32
	stp x8, x9, [sp, #16]
	stp x0, x1, [sp, #0]
	bl GetReturnAddr
	mov lr, x0
	ldp x0, x1, [sp, #0]
	ldp x8, x9, [sp, #16]
	add sp, sp, #32
	ret

.ReturnCachingGadget:
	add x0, sp, #264
	bl PrepareReturnCaching

	ldr d0, [sp, #8]
	ldp d1, d2, [sp, #16]
	ldp d3, d4, [sp, #32]
	ldp d5, d6, [sp, #48]
	ldp d7, d8, [sp, #64]
	ldp d9, d10, [sp, #80]
	ldp d11, d12, [sp, #96]
	ldp d13, d14, [sp, #112]
	ldp d15, d16, [sp, #128]
	ldp d17, d18, [sp, #144]
	ldp d19, d20, [sp, #160]
	ldp d21, d22, [sp, #176]
	ldp d23, d24, [sp, #192]
	ldp d25, d26, [sp, #208]
	ldp d27, d28, [sp, #224]
	ldp d29, d30, [sp, #240]
	ldr x0, [sp, #264]
	ldp x1, x2, [sp, #272]
	ldp x3, x4, [sp, #288]
	ldp x5, x6, [sp, #304]
	ldp x7, x8, [sp, #320]
	ldp x9, x10, [sp, #336]
	ldp x11, x12, [sp, #352]
	ldp x13, x14, [sp, #368]
	ldp x15, x16, [sp, #384]
	ldp x17, x18, [sp, #400]
	ldp x19, x20, [sp, #416]
	ldp x21, x22, [sp, #432]
	ldp x23, x24, [sp, #448]
	ldp x25, x26, [sp, #464]
	ldp x27, x28, [sp, #480]
	ldp x29, lr, [sp, #496]

	ldr x9, [x0, #48]
	blr x9

	str x29, [sp, #496]
	stp x27, x28, [sp, #480]
	stp x25, x26, [sp, #464]
	stp x23, x24, [sp, #448]
	stp x21, x22, [sp, #432]
	stp x19, x20, [sp, #416]
	stp x17, x18, [sp, #400]
	stp x15, x16, [sp, #384]
	stp x13, x14, [sp, #368]
	stp x11, x12, [sp, #352]
	stp x9, x10, [sp, #336]
	stp x7, x8, [sp, #320]
	stp x5, x6, [sp, #304]
	stp x3, x4, [sp, #288]
	stp x1, x2, [sp, #272]
	str x0, [sp, #264]
	stp d29, d30, [sp, #240]
	stp d27, d28, [sp, #224]
	stp d25, d26, [sp, #208]
	stp d23, d24, [sp, #192]
	stp d21, d22, [sp, #176]
	stp d19, d20, [sp, #160]
	stp d17, d18, [sp, #144]
	stp d15, d16, [sp, #128]
	stp d13, d14, [sp, #112]
	stp d11, d12, [sp, #96]
	stp d9, d10, [sp, #80]
	stp d7, d8, [sp, #64]
	stp d5, d6, [sp, #48]
	stp d3, d4, [sp, #32]
	stp d1, d2, [sp, #16]
	str d0, [sp, #8]

	add x1, sp, #512
	bl ReturnCachingNative
	mov lr, x0
	
	ldr d0, [sp, #8]
	ldp d1, d2, [sp, #16]
	ldp d3, d4, [sp, #32]
	ldp d5, d6, [sp, #48]
	ldp d7, d8, [sp, #64]
	ldp d9, d10, [sp, #80]
	ldp d11, d12, [sp, #96]
	ldp d13, d14, [sp, #112]
	ldp d15, d16, [sp, #128]
	ldp d17, d18, [sp, #144]
	ldp d19, d20, [sp, #160]
	ldp d21, d22, [sp, #176]
	ldp d23, d24, [sp, #192]
	ldp d25, d26, [sp, #208]
	ldp d27, d28, [sp, #224]
	ldp d29, d30, [sp, #240]
	ldr x0, [sp, #264]
	ldp x1, x2, [sp, #272]
	ldp x3, x4, [sp, #288]
	ldp x5, x6, [sp, #304]
	ldp x7, x8, [sp, #320]
	ldp x9, x10, [sp, #336]
	ldp x11, x12, [sp, #352]
	ldp x13, x14, [sp, #368]
	ldp x15, x16, [sp, #384]
	ldp x17, x18, [sp, #400]
	ldp x19, x20, [sp, #416]
	ldp x21, x22, [sp, #432]
	ldp x23, x24, [sp, #448]
	ldp x25, x26, [sp, #464]
	ldp x27, x28, [sp, #480]
	ldr x29, [sp, #496]
	
	add sp, sp, #512
	ret

.RpcCachingGadget:
	add x0, sp, #264
	add x1, sp, #8
	add x2, sp, #512
	bl RpcCachingNative

	ldr d0, [sp, #8]
	ldp d1, d2, [sp, #16]
	ldp d3, d4, [sp, #32]
	ldp d5, d6, [sp, #48]
	ldp d7, d8, [sp, #64]
	ldp d9, d10, [sp, #80]
	ldp d11, d12, [sp, #96]
	ldp d13, d14, [sp, #112]
	ldp d15, d16, [sp, #128]
	ldp d17, d18, [sp, #144]
	ldp d19, d20, [sp, #160]
	ldp d21, d22, [sp, #176]
	ldp d23, d24, [sp, #192]
	ldp d25, d26, [sp, #208]
	ldp d27, d28, [sp, #224]
	ldp d29, d30, [sp, #240]
	ldr x0, [sp, #264]
	ldp x1, x2, [sp, #272]
	ldp x3, x4, [sp, #288]
	ldp x5, x6, [sp, #304]
	ldp x7, x8, [sp, #320]
	ldp x9, x10, [sp, #336]
	ldp x11, x12, [sp, #352]
	ldp x13, x14, [sp, #368]
	ldp x15, x16, [sp, #384]
	ldp x17, x18, [sp, #400]
	ldp x19, x20, [sp, #416]
	ldp x21, x22, [sp, #432]
	ldp x23, x24, [sp, #448]
	ldp x25, x26, [sp, #464]
	ldp x27, x28, [sp, #480]
	ldp x29, lr, [sp, #496]
	add sp, sp, #512

	ldr x9, [x0, #48]
	blr x9

	sub sp, sp, #32
	stp x8, x9, [sp, #16]
	stp x0, x1, [sp, #0]
	bl GetReturnAddr
	mov lr, x0
	ldp x0, x1, [sp, #0]
	ldp x8, x9, [sp, #16]
	add sp, sp, #32

	ret

.DummyCallGadget:
	add x0, sp, #264
	add x1, sp, #512
	bl ExecuteCacheNative

	add sp, sp, #512

	sub sp, sp, #32
	stp x8, x9, [sp, #16]
	stp x0, x1, [sp, #0]
	bl GetReturnAddr
	mov lr, x0
	ldp x0, x1, [sp, #0]
	ldp x8, x9, [sp, #16]
	add sp, sp, #32
	fmov d0, x0
	fmov d1, x1
	ret

.OriginalGadget:
	ldr d0, [sp, #8]
	ldp d1, d2, [sp, #16]
	ldp d3, d4, [sp, #32]
	ldp d5, d6, [sp, #48]
	ldp d7, d8, [sp, #64]
	ldp d9, d10, [sp, #80]
	ldp d11, d12, [sp, #96]
	ldp d13, d14, [sp, #112]
	ldp d15, d16, [sp, #128]
	ldp d17, d18, [sp, #144]
	ldp d19, d20, [sp, #160]
	ldp d21, d22, [sp, #176]
	ldp d23, d24, [sp, #192]
	ldp d25, d26, [sp, #208]
	ldp d27, d28, [sp, #224]
	ldp d29, d30, [sp, #240]
	ldr x0, [sp, #264]
	ldp x1, x2, [sp, #272]
	ldp x3, x4, [sp, #288]
	ldp x5, x6, [sp, #304]
	ldp x7, x8, [sp, #320]
	ldp x9, x10, [sp, #336]
	ldp x11, x12, [sp, #352]
	ldp x13, x14, [sp, #368]
	ldp x15, x16, [sp, #384]
	ldp x17, x18, [sp, #400]
	ldp x19, x20, [sp, #416]
	ldp x21, x22, [sp, #432]
	ldp x23, x24, [sp, #448]
	ldp x25, x26, [sp, #464]
	ldp x27, x28, [sp, #480]
	ldp x29, lr, [sp, #496]
	add sp, sp, #512

	ldr x16, [x0, #48]
	br x16

.SkipOriginalGadget:
	ldr d0, [sp, #8]
	ldp d1, d2, [sp, #16]
	ldp d3, d4, [sp, #32]
	ldp d5, d6, [sp, #48]
	ldp d7, d8, [sp, #64]
	ldp d9, d10, [sp, #80]
	ldp d11, d12, [sp, #96]
	ldp d13, d14, [sp, #112]
	ldp d15, d16, [sp, #128]
	ldp d17, d18, [sp, #144]
	ldp d19, d20, [sp, #160]
	ldp d21, d22, [sp, #176]
	ldp d23, d24, [sp, #192]
	ldp d25, d26, [sp, #208]
	ldp d27, d28, [sp, #224]
	ldp d29, d30, [sp, #240]
	ldr x0, [sp, #264]
	ldp x1, x2, [sp, #272]
	ldp x3, x4, [sp, #288]
	ldp x5, x6, [sp, #304]
	ldp x7, x8, [sp, #320]
	ldp x9, x10, [sp, #336]
	ldp x11, x12, [sp, #352]
	ldp x13, x14, [sp, #368]
	ldp x15, x16, [sp, #384]
	ldp x17, x18, [sp, #400]
	ldp x19, x20, [sp, #416]
	ldp x21, x22, [sp, #432]
	ldp x23, x24, [sp, #448]
	ldp x25, x26, [sp, #464]
	ldp x27, x28, [sp, #480]
	ldp x29, lr, [sp, #496]
	add sp, sp, #512
	ret
