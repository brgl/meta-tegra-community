DESCRIPTION = "NVIDIA Deepstream SDK"
HOMEPAGE = "https://developer.nvidia.com/deepstream-sdk"
LICENSE = "Proprietary"
LIC_FILES_CHKSUM = " \
    file://usr/share/doc/deepstream-6.0/copyright;md5=7a5ffdb833cdefe5ac34aaa81de173a3 \
    file://opt/nvidia/deepstream/deepstream-6.0/LICENSE.txt;md5=5dcf86799aa20202668e226e93f9cfd9 \
    file://opt/nvidia/deepstream/deepstream-6.0/doc/nvidia-tegra/LICENSE.iothub_client;md5=4f8c6347a759d246b5f96281726b8611 \
    file://opt/nvidia/deepstream/deepstream-6.0/doc/nvidia-tegra/LICENSE.nvds_amqp_protocol_adaptor;md5=8b4b651fa4090272b2e08e208140a658 \
"

inherit l4t_deb_pkgfeed

SRC_COMMON_DEBS = "${BPN}_${PV}_arm64.deb;subdir=${BPN}"
SRC_URI[sha256sum] = "320927fe83b40bea2da5408f6777985065038c7f1506b2f5d5eaa0bcfc2a1564"

COMPATIBLE_MACHINE = "(tegra)"
PACKAGE_ARCH = "${TEGRA_PKGARCH}"

PACKAGECONFIG ??= ""
PACKAGECONFIG[amqp] = ",,rabbitmq-c"
PACKAGECONFIG[kafka] = ",,librdkafka"
# NB: requires hiredis 1.0.0+
PACKAGECONFIG[redis] = ",,hiredis"
# NB: need recipes for these dependencies
PACKAGECONFIG[azure] = ""
PACKAGECONFIG[triton] = ""
PACKAGECONFIG[rivermax] = ""

DEPENDS = "glib-2.0 gstreamer1.0 gstreamer1.0-plugins-base gstreamer1.0-rtsp-server \
    tensorrt-core tensorrt-plugins libnvvpi1 libvisionworks libnpp json-glib \
    openssl111 tegra-libraries-multimedia \
"

S = "${WORKDIR}/${BPN}"
B = "${WORKDIR}/build"

DEEPSTREAM_PATH = "/opt/nvidia/deepstream/deepstream-6.0"
SYSROOT_DIRS += "${DEEPSTREAM_PATH}/lib/"

do_configure() {
    for feature in azure amqp kafka redis triton rivermax; do
        if ! echo "${PACKAGECONFIG}" | grep -q "$feature"; then
            rm -f ${S}${DEEPSTREAM_PATH}/lib/libnvds_${feature}*
            if [ "$feature" = "azure" ]; then
                rm -f ${S}${DEEPSTREAM_PATH}/lib/libiothub_client.so
            fi
            if [ "$feature" = "triton" ]; then
                rm -f ${S}${DEEPSTREAM_PATH}/lib/gst-plugins/libnvdsgst_inferserver.so
                rm -f ${S}${DEEPSTREAM_PATH}/lib/libnvds_infer_server.so
            fi
            if [ "$feature" = "rivermax" ]; then
                rm -f ${S}${DEEPSTREAM_PATH}/lib/gst-plugins/libnvdsgst_udp.so
            fi
        fi
    done
}

do_install() {
    install -d ${D}${bindir}/
    install -m 0755 ${S}${DEEPSTREAM_PATH}/bin/* ${D}${bindir}/

    install -d ${D}${DEEPSTREAM_PATH}/lib/
    for f in ${S}${DEEPSTREAM_PATH}/lib/*; do
        [ ! -d "$f" ] || continue
        install -m 0644 "$f" ${D}${DEEPSTREAM_PATH}/lib/
    done
    ln -sf libnvds_msgconv.so.1.0.0 ${D}${DEEPSTREAM_PATH}/lib/libnvds_msgconv.so
    ln -sf libnvds_msgconv_audio.so.1.0.0 ${D}${DEEPSTREAM_PATH}/lib/libnvds_msgconv_audio.so

    cp -R --preserve=mode,timestamps ${S}${DEEPSTREAM_PATH}/lib/cvcore_libs/ ${D}${DEEPSTREAM_PATH}/lib/

    install -d ${D}/${sysconfdir}/ld.so.conf.d/
    echo "${DEEPSTREAM_PATH}/lib" > ${D}/${sysconfdir}/ld.so.conf.d/deepstream.conf
    echo "${libdir}/gstreamer-1.0/deepstream" >> ${D}/${sysconfdir}/ld.so.conf.d/deepstream.conf

    install -d ${D}${libdir}/gstreamer-1.0/deepstream
    install -m 0644 ${S}${DEEPSTREAM_PATH}/lib/gst-plugins/* ${D}${libdir}/gstreamer-1.0/deepstream/

    cp -R --preserve=mode,timestamps ${S}${DEEPSTREAM_PATH}/samples ${D}${DEEPSTREAM_PATH}/

    install -d ${D}${includedir}/deepstream
    cp -R --preserve=mode,timestamps ${S}${DEEPSTREAM_PATH}/sources/includes/* ${D}${includedir}/

    cp -R --preserve=mode,timestamps ${S}${DEEPSTREAM_PATH}/sources/ ${D}${DEEPSTREAM_PATH}/
}

INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INHIBIT_SYSROOT_STRIP = "1"
INSANE_SKIP = "dev-so ldflags"

def pkgconf_packages(d):
    pkgconf = bb.utils.filter('PACKAGECONFIG', 'azure amqp kafka redis triton rivermax', d).split()
    pn = d.getVar('PN')
    return ' '.join(['{}-{}'.format(pn, p) for p in pkgconf])

PKGCONF_PACKAGES = "${@pkgconf_packages(d)}"

PACKAGES =+ "${PN}-samples-data ${PN}-samples ${PN}-sources ${PKGCONF_PACKAGES}"

FILES:${PN} = "\
    ${sysconfdir}/ld.so.conf.d/  \
    ${libdir}/gstreamer-1.0/deepstream \
    ${DEEPSTREAM_PATH}/lib \
"

FILES:${PN}-dev = "${includedir}"

FILES:${PN}-samples = "${bindir}/*"
FILES:${PN}-samples-data = "\
    ${DEEPSTREAM_PATH}/samples \
    ${DEEPSTREAM_PATH}/sources/apps/sample_apps/*/*.txt \
    ${DEEPSTREAM_PATH}/sources/apps/sample_apps/*/README \
    ${DEEPSTREAM_PATH}/sources/apps/sample_apps/*/configs/ \
    ${DEEPSTREAM_PATH}/sources/apps/sample_apps/*/inferserver/ \
    ${DEEPSTREAM_PATH}/sources/apps/sample_apps/*/csv_files/ \
"

FILES:${PN}-sources = "${DEEPSTREAM_PATH}/sources"

FILES:${PN}-azure = "${DEEPSTREAM_PATH}/lib/libiothub_client.so ${DEEPSTREAM_PATH}/lib/libnvds_azure*"
FILES:${PN}-triton = "\
    ${libdir}/gstreamer-1.0/deepstream/libnvdsgst_inferserver.so \
    ${DEEPSTREAM_PATH}/lib/libnvds_infer_server.so \
"
FILES:${PN}-amqp = "${DEEPSTREAM_PATH}/lib/libnvds_amqp*"
FILES:${PN}-kafka = "${DEEPSTREAM_PATH}/lib/libnvds_kafka*"
FILES:${PN}-redis = "${DEEPSTREAM_PATH}/lib/libnvds_redis*"
FILES:${PN}-rivermax = "${libdir}/gstreamer-1.0/deepstream/libnvdsgst_udp.so"

RDEPENDS:${PN} = "libvisionworks-devso-symlink"
RDEPENDS:${PN}-samples = "${PN}-samples-data"
RDEPENDS:${PN}-samples-data = "bash"
RDEPENDS:${PN}-sources = "bash ${PN}-samples-data ${PN}"
RRECOMMENDS:${PN} = "liberation-fonts"
