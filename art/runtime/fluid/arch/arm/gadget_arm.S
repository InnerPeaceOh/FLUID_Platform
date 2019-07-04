.global RpcGadgetTrampoline
RpcGadgetTrampoline:
	sub sp, sp, #224
	str lr, [sp, #220]
	str r12, [sp, #216]
	strd r10, r11, [sp, #208]
	strd r8, r9, [sp, #200]
	strd r6, r7, [sp, #192]
	strd r4, r5, [sp, #184]
	strd r2, r3, [sp, #176]
	strd r0, r1, [sp, #168]
	vstr s31, [sp, #164]
	vstr s30, [sp, #160]
	vstr s29, [sp, #156]
	vstr s28, [sp, #152]
	vstr s27, [sp, #148]
	vstr s26, [sp, #144]
	vstr s25, [sp, #140]
	vstr s24, [sp, #136]
	vstr s23, [sp, #132]
	vstr s22, [sp, #128]
	vstr s21, [sp, #124]
	vstr s20, [sp, #120]
	vstr s19, [sp, #116]
	vstr s18, [sp, #112]
	vstr s17, [sp, #108]
	vstr s16, [sp, #104]
	vstr s15, [sp, #100]
	vstr s14, [sp, #96]
	vstr s13, [sp, #92]
	vstr s12, [sp, #88]
	vstr s11, [sp, #84]
	vstr s10, [sp, #80]
	vstr s9, [sp, #76]
	vstr s8, [sp, #72]
	vstr s7, [sp, #68]
	vstr s6, [sp, #64]
	vstr s5, [sp, #60]
	vstr s4, [sp, #56]
	vstr s3, [sp, #52]
	vstr s2, [sp, #48]
	vstr s1, [sp, #44]
	vstr s0, [sp, #40]

	add r2, sp, #224
	bl NeedRpcTranslation
	cmp r0, #0
	beq .OriginalGadget
	cmp r0, #1
	beq .RpcTranslationGadget
	cmp r0, #2
	beq .DummyCallGadget
	cmp r0, #3
	beq .ReturnCachingGadget
	cmp r0, #4
	beq .RpcCachingGadget
	cmp r0, #5
	beq .SkipOriginalGadget

.RpcTranslationGadget:
	add r0, sp, #168
	add r1, sp, #40
	add r2, sp, #224
	bl RpcTranslationNative

	vldr s0, [sp, #40]
	vldr s1, [sp, #44]
	vldr s2, [sp, #48]
	vldr s3, [sp, #52]
	vldr s4, [sp, #56]
	vldr s5, [sp, #60]
	vldr s6, [sp, #64]
	vldr s7, [sp, #68]
	vldr s8, [sp, #72]
	vldr s9, [sp, #76]
	vldr s10, [sp, #80]
	vldr s11, [sp, #84]
	vldr s12, [sp, #88]
	vldr s13, [sp, #92]
	vldr s14, [sp, #96]
	vldr s15, [sp, #100]
	vldr s16, [sp, #104]
	vldr s17, [sp, #108]
	vldr s18, [sp, #112]
	vldr s19, [sp, #116]
	vldr s20, [sp, #120]
	vldr s21, [sp, #124]
	vldr s22, [sp, #128]
	vldr s23, [sp, #132]
	vldr s24, [sp, #136]
	vldr s25, [sp, #140]
	vldr s26, [sp, #144]
	vldr s27, [sp, #148]
	vldr s28, [sp, #152]
	vldr s29, [sp, #156]
	vldr s30, [sp, #160]
	vldr s31, [sp, #164]
	ldrd r0, r1, [sp, #168]
	ldrd r2, r3, [sp, #176]
	ldrd r4, r5, [sp, #184]
	ldrd r6, r7, [sp, #192]
	ldrd r8, r9, [sp, #200]
	ldrd r10, r11, [sp, #208]
	ldr r12, [sp, #216]
	ldr lr, [sp, #220]
	add sp, sp, #224

	ldr lr, [r0, #32]
	blx lr

	push {r0, r1, r2}
	bl GetReturnAddr
	mov lr, r0
	pop {r0, r1, r2}
	bx lr

.ReturnCachingGadget:
	add r0, sp, #168
	bl PrepareReturnCaching

	vldr s0, [sp, #40]
	vldr s1, [sp, #44]
	vldr s2, [sp, #48]
	vldr s3, [sp, #52]
	vldr s4, [sp, #56]
	vldr s5, [sp, #60]
	vldr s6, [sp, #64]
	vldr s7, [sp, #68]
	vldr s8, [sp, #72]
	vldr s9, [sp, #76]
	vldr s10, [sp, #80]
	vldr s11, [sp, #84]
	vldr s12, [sp, #88]
	vldr s13, [sp, #92]
	vldr s14, [sp, #96]
	vldr s15, [sp, #100]
	vldr s16, [sp, #104]
	vldr s17, [sp, #108]
	vldr s18, [sp, #112]
	vldr s19, [sp, #116]
	vldr s20, [sp, #120]
	vldr s21, [sp, #124]
	vldr s22, [sp, #128]
	vldr s23, [sp, #132]
	vldr s24, [sp, #136]
	vldr s25, [sp, #140]
	vldr s26, [sp, #144]
	vldr s27, [sp, #148]
	vldr s28, [sp, #152]
	vldr s29, [sp, #156]
	vldr s30, [sp, #160]
	vldr s31, [sp, #164]
	ldrd r0, r1, [sp, #168]
	ldrd r2, r3, [sp, #176]
	ldrd r4, r5, [sp, #184]
	ldrd r6, r7, [sp, #192]
	ldrd r8, r9, [sp, #200]
	ldrd r10, r11, [sp, #208]
	ldr r12, [sp, #216]
	ldr lr, [sp, #220]

	ldr lr, [r0, #32]
	blx lr

	str r12, [sp, #216]
	strd r10, r11, [sp, #208]
	strd r8, r9, [sp, #200]
	strd r6, r7, [sp, #192]
	strd r4, r5, [sp, #184]
	strd r2, r3, [sp, #176]
	strd r0, r1, [sp, #168]
	vstr s31, [sp, #164]
	vstr s30, [sp, #160]
	vstr s29, [sp, #156]
	vstr s28, [sp, #152]
	vstr s27, [sp, #148]
	vstr s26, [sp, #144]
	vstr s25, [sp, #140]
	vstr s24, [sp, #136]
	vstr s23, [sp, #132]
	vstr s22, [sp, #128]
	vstr s21, [sp, #124]
	vstr s20, [sp, #120]
	vstr s19, [sp, #116]
	vstr s18, [sp, #112]
	vstr s17, [sp, #108]
	vstr s16, [sp, #104]
	vstr s15, [sp, #100]
	vstr s14, [sp, #96]
	vstr s13, [sp, #92]
	vstr s12, [sp, #88]
	vstr s11, [sp, #84]
	vstr s10, [sp, #80]
	vstr s9, [sp, #76]
	vstr s8, [sp, #72]
	vstr s7, [sp, #68]
	vstr s6, [sp, #64]
	vstr s5, [sp, #60]
	vstr s4, [sp, #56]
	vstr s3, [sp, #52]
	vstr s2, [sp, #48]
	vstr s1, [sp, #44]
	vstr s0, [sp, #40]

	add r1, sp, #224
	bl ReturnCachingNative
	mov lr, r0

	vldr s0, [sp, #40]
	vldr s1, [sp, #44]
	vldr s2, [sp, #48]
	vldr s3, [sp, #52]
	vldr s4, [sp, #56]
	vldr s5, [sp, #60]
	vldr s6, [sp, #64]
	vldr s7, [sp, #68]
	vldr s8, [sp, #72]
	vldr s9, [sp, #76]
	vldr s10, [sp, #80]
	vldr s11, [sp, #84]
	vldr s12, [sp, #88]
	vldr s13, [sp, #92]
	vldr s14, [sp, #96]
	vldr s15, [sp, #100]
	vldr s16, [sp, #104]
	vldr s17, [sp, #108]
	vldr s18, [sp, #112]
	vldr s19, [sp, #116]
	vldr s20, [sp, #120]
	vldr s21, [sp, #124]
	vldr s22, [sp, #128]
	vldr s23, [sp, #132]
	vldr s24, [sp, #136]
	vldr s25, [sp, #140]
	vldr s26, [sp, #144]
	vldr s27, [sp, #148]
	vldr s28, [sp, #152]
	vldr s29, [sp, #156]
	vldr s30, [sp, #160]
	vldr s31, [sp, #164]
	ldrd r0, r1, [sp, #168]
	ldrd r2, r3, [sp, #176]
	ldrd r4, r5, [sp, #184]
	ldrd r6, r7, [sp, #192]
	ldrd r8, r9, [sp, #200]
	ldrd r10, r11, [sp, #208]
	ldr r12, [sp, #216]

	add sp, sp, #224
	bx lr

.RpcCachingGadget:
	add r0, sp, #168
	add r1, sp, #40
	add r2, sp, #224
	bl RpcCachingNative

	vldr s0, [sp, #40]
	vldr s1, [sp, #44]
	vldr s2, [sp, #48]
	vldr s3, [sp, #52]
	vldr s4, [sp, #56]
	vldr s5, [sp, #60]
	vldr s6, [sp, #64]
	vldr s7, [sp, #68]
	vldr s8, [sp, #72]
	vldr s9, [sp, #76]
	vldr s10, [sp, #80]
	vldr s11, [sp, #84]
	vldr s12, [sp, #88]
	vldr s13, [sp, #92]
	vldr s14, [sp, #96]
	vldr s15, [sp, #100]
	vldr s16, [sp, #104]
	vldr s17, [sp, #108]
	vldr s18, [sp, #112]
	vldr s19, [sp, #116]
	vldr s20, [sp, #120]
	vldr s21, [sp, #124]
	vldr s22, [sp, #128]
	vldr s23, [sp, #132]
	vldr s24, [sp, #136]
	vldr s25, [sp, #140]
	vldr s26, [sp, #144]
	vldr s27, [sp, #148]
	vldr s28, [sp, #152]
	vldr s29, [sp, #156]
	vldr s30, [sp, #160]
	vldr s31, [sp, #164]
	ldrd r0, r1, [sp, #168]
	ldrd r2, r3, [sp, #176]
	ldrd r4, r5, [sp, #184]
	ldrd r6, r7, [sp, #192]
	ldrd r8, r9, [sp, #200]
	ldrd r10, r11, [sp, #208]
	ldr r12, [sp, #216]
	ldr lr, [sp, #220]
	add sp, sp, #224

	ldr lr, [r0, #32]
	blx lr

	push {r0, r1, r2}
	bl GetReturnAddr
	mov lr, r0
	pop {r0, r1, r2}
	bx lr

.DummyCallGadget:
	sub sp, sp, #8

	add r0, sp, #176
	add r1, sp, #232
	add r2, sp, #0
	bl ExecuteCacheNative

	push {r0, r1, r2}
	bl GetReturnAddr
	mov lr, r0
	pop {r0, r1, r2}

	ldrd r0, r1, [sp, #0]
	vmov s0, r0
	vmov s1, r1
	add sp, sp, #232
	bx lr

.OriginalGadget:
	vldr s0, [sp, #40]
	vldr s1, [sp, #44]
	vldr s2, [sp, #48]
	vldr s3, [sp, #52]
	vldr s4, [sp, #56]
	vldr s5, [sp, #60]
	vldr s6, [sp, #64]
	vldr s7, [sp, #68]
	vldr s8, [sp, #72]
	vldr s9, [sp, #76]
	vldr s10, [sp, #80]
	vldr s11, [sp, #84]
	vldr s12, [sp, #88]
	vldr s13, [sp, #92]
	vldr s14, [sp, #96]
	vldr s15, [sp, #100]
	vldr s16, [sp, #104]
	vldr s17, [sp, #108]
	vldr s18, [sp, #112]
	vldr s19, [sp, #116]
	vldr s20, [sp, #120]
	vldr s21, [sp, #124]
	vldr s22, [sp, #128]
	vldr s23, [sp, #132]
	vldr s24, [sp, #136]
	vldr s25, [sp, #140]
	vldr s26, [sp, #144]
	vldr s27, [sp, #148]
	vldr s28, [sp, #152]
	vldr s29, [sp, #156]
	vldr s30, [sp, #160]
	vldr s31, [sp, #164]
	ldrd r0, r1, [sp, #168]
	ldrd r2, r3, [sp, #176]
	ldrd r4, r5, [sp, #184]
	ldrd r6, r7, [sp, #192]
	ldrd r8, r9, [sp, #200]
	ldrd r10, r11, [sp, #208]
	ldr r12, [sp, #216]
	ldr lr, [sp, #220]
	add sp, sp, #224

	ldr r12, [r0, #32]
	bx r12

.SkipOriginalGadget:
	vldr s0, [sp, #40]
	vldr s1, [sp, #44]
	vldr s2, [sp, #48]
	vldr s3, [sp, #52]
	vldr s4, [sp, #56]
	vldr s5, [sp, #60]
	vldr s6, [sp, #64]
	vldr s7, [sp, #68]
	vldr s8, [sp, #72]
	vldr s9, [sp, #76]
	vldr s10, [sp, #80]
	vldr s11, [sp, #84]
	vldr s12, [sp, #88]
	vldr s13, [sp, #92]
	vldr s14, [sp, #96]
	vldr s15, [sp, #100]
	vldr s16, [sp, #104]
	vldr s17, [sp, #108]
	vldr s18, [sp, #112]
	vldr s19, [sp, #116]
	vldr s20, [sp, #120]
	vldr s21, [sp, #124]
	vldr s22, [sp, #128]
	vldr s23, [sp, #132]
	vldr s24, [sp, #136]
	vldr s25, [sp, #140]
	vldr s26, [sp, #144]
	vldr s27, [sp, #148]
	vldr s28, [sp, #152]
	vldr s29, [sp, #156]
	vldr s30, [sp, #160]
	vldr s31, [sp, #164]
	ldrd r0, r1, [sp, #168]
	ldrd r2, r3, [sp, #176]
	ldrd r4, r5, [sp, #184]
	ldrd r6, r7, [sp, #192]
	ldrd r8, r9, [sp, #200]
	ldrd r10, r11, [sp, #208]
	ldr r12, [sp, #216]
	ldr lr, [sp, #220]
	add sp, sp, #224
	bx lr
