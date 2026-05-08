package ru.basnukaev.argumentmap.library.service;

import java.util.List;

import ru.basnukaev.argumentmap.library.domain.ImageRegion;
import ru.basnukaev.argumentmap.library.domain.Page;

public record PageDetail(Page page, List<ImageRegion> regions) {
}
