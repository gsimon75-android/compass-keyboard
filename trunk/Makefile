PROJECT=ck
OUTDIR=bin
PRIVKEY=/root/GaborSimon.key
CERT=/root/GS_apk.crt

.PHONY:     				build

sign:					$(OUTDIR)/$(PROJECT).apk

build:					
					ant debug

$(OUTDIR)/$(PROJECT)-release.apk:
					ant release

$(OUTDIR)/$(PROJECT)-debug.apk:
					ant debug

$(OUTDIR)/$(PROJECT)-signed.apk:	$(OUTDIR)/$(PROJECT)-debug.apk
					apksigner.sh -i $^ -o $@ -k $(PRIVKEY) -c $(CERT)

$(OUTDIR)/$(PROJECT).apk:		$(OUTDIR)/$(PROJECT)-signed.apk
					zipalign -f 4 $^ $@

# Why not ant?
# 1. it cannot invoke external commands in a sensible way
# 2. it cannot invoke external tools so that they have stdin/out/err
# 3. it cannot represent an 'A-depends-on-B' relationship
# 4. what it implements is more an independent ruleset than a production tree of target items
# 5. typical java tool: it knows the j-world and doesn't care of anything else
# 6. this environment is unix, not java

# vim: set ai si sw=8 ts=8 noet:
